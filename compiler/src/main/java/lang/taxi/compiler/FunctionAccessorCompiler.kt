package lang.taxi.compiler

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import lang.taxi.CompilationError
import lang.taxi.Namespace
import lang.taxi.TaxiParser
import lang.taxi.TaxiParser.ArgumentContext
import lang.taxi.accessors.Accessor
import lang.taxi.accessors.LiteralAccessor
import lang.taxi.expressions.Expression
import lang.taxi.expressions.TypeExpression
import lang.taxi.findNamespace
import lang.taxi.functions.Function
import lang.taxi.functions.FunctionAccessor
import lang.taxi.services.Parameter
import lang.taxi.source
import lang.taxi.text
import lang.taxi.toCompilationUnit
import lang.taxi.types.FieldReferenceSelector
import lang.taxi.types.PrimitiveType
import lang.taxi.types.StreamType
import lang.taxi.types.Type
import lang.taxi.types.TypeArgument
import lang.taxi.types.TypeChecker
import lang.taxi.types.TypeReferenceSelector
import lang.taxi.utils.createCompilationError
import lang.taxi.utils.createInternalError
import lang.taxi.utils.flattenErrors
import lang.taxi.utils.invertEitherList
import lang.taxi.utils.wrapErrorsInList
import lang.taxi.value
import org.antlr.v4.runtime.ParserRuleContext

interface FunctionParameterReferenceResolver {
   fun compileScalarAccessor(
      expression: TaxiParser.ScalarAccessorExpressionContext,
      targetType: Type = PrimitiveType.ANY
   ): Either<List<CompilationError>, Accessor>

   fun compileFieldReferenceAccessor(
      function: Function,
      parameterContext: TaxiParser.ArgumentContext
   ): Either<List<CompilationError>, FieldReferenceSelector>

   fun parseTypeMemberReference(
      lhsExpressionGroup: TaxiParser.ExpressionGroupContext,
      typeMemberReference: TaxiParser.MemberReferenceContext
   ): Either<List<CompilationError>, Expression>
}

class FunctionAccessorCompiler(
   private val tokenProcessor: TokenProcessor,
   private val typeChecker: TypeChecker,
   private val errors: MutableList<CompilationError>,
   private val referenceResolver: FunctionParameterReferenceResolver,
) {
   companion object {
      fun buildAndResolveTypeArgumentsOrError(
         function: Function,
         parameters: List<Accessor>,
         targetType: Type,
         context: ParserRuleContext
      ): Either<List<CompilationError>, FunctionAccessor> {
         return try {
            FunctionAccessor.buildAndResolveTypeArguments(function, parameters, targetType).right()
         } catch (e: Exception) {
            listOf(CompilationError(context.toCompilationUnit(), e.message!!)).left()
         }
      }

   }

   internal fun buildFunctionAccessor(
      namespace: String,
      functionName: String,
      context: ParserRuleContext, // either A TaxiParser.FunctionCallContext or a TaxiParser.MethodCallContext
      arguments: List<ArgumentContext>,
      targetType: Type,
      receiver: Expression? = null,

      ): Either<List<CompilationError>, FunctionAccessor> {
      return tokenProcessor.attemptToLookupSymbolByName(
         namespace,
         functionName,
         context,
         symbolKind = SymbolKind.FUNCTION
      )
         .wrapErrorsInList()
         .flatMap { qualifiedName ->
            tokenProcessor.resolveFunction(qualifiedName, context).flatMap { function ->
               require(function.isDefined) { "Function should have already been compiled before evaluation in a read function expression" }
               typeChecker.assertIsAssignable(function.returnType!!, targetType, context)
                  ?.let { compilationError ->
                     errors.add(compilationError)
                  }
               receiver?.let { receiver ->
                  val firstParam = function.parameters.firstOrNull()
                  // In theory, this isn't possible, as the compiler will catch it earlier. But, belts 'n' braces
                     ?: return@flatMap listOf(
                        CompilationError(
                           context.toCompilationUnit(),
                           "Function ${function.qualifiedName} can not be called as an extension function, as it does not take any params"
                        )
                     )
                        .left()
                  typeChecker.assertIsAssignable(receiver.returnType, firstParam.type, context)
                     ?.let { compilationError ->
                        return@flatMap listOf(compilationError).left()
                     }

               }

               val parametersOrErrors: Either<List<CompilationError>, List<Accessor>> = run {

                  // First handle regular (non-vararg) parameters
                  val nonVarArgParamsOrErrors = function.parameters
                     .filter { !it.isVarArg }
                     .mapIndexed { parameterIndex, parameter ->

                        val argumentInputIndex = if (receiver != null) parameterIndex - 1 else parameterIndex
                        val argumentInputContext = arguments.getOrNull(argumentInputIndex)
                        val parameterType = function.getParameterType(parameterIndex)
                        val parameterAccessor: Either<List<CompilationError>, Accessor> = when {
                           parameterIndex == 0 && receiver != null -> receiver.right()
                           argumentInputContext == null && parameter.defaultValue != null -> parameter.defaultValue!!.right()
                           argumentInputContext == null -> context.createCompilationError("No value provided for parameter ${parameter.name} on function $functionName, and no default value is defined")
                           else -> compileArgument(
                              argumentInputContext,
                              function,
                              namespace,
                              parameter,
                              parameterType,
                           )

                        }
                        parameterAccessor
                     }

                  // Now handle varargs (if present)
                  val varargParam = function.parameters.lastOrNull()?.let { if (it.isVarArg) it else null }
                  val varArgAccessorsOrErrors = if (varargParam != null) {
                     val varArgIndex = function.parameters.indexOf(varargParam).let {
                        if (receiver != null) it - 1 else it
                     }
                     val varArgInputs = arguments.drop(varArgIndex)
                     varArgInputs.map { argumentInputContext ->
                        compileArgument(argumentInputContext,
                           function,
                           namespace,
                           varargParam,
                           function.getParameterType(function.parameters.indexOf(varargParam)))
                     }
                  } else emptyList()

                  (nonVarArgParamsOrErrors + varArgAccessorsOrErrors).invertEitherList().flattenErrors()
               }
               parametersOrErrors.flatMap { parameters: List<Accessor> ->
                  // If we're invoked as an extension function, we'll be passed a receiver, which
                  // is to be used as the first parameter
                  val allParams = if (receiver != null) {
                     val unwrappedReceiver =
                        if (receiver is TypeExpression && StreamType.isStream(receiver.type) && function.parameters.firstOrNull()?.type is TypeArgument) {
                           // If the receiver is Stream<T>, unwrap it to <T>.
                           // Functions don't operate on Streams, but on the items that the stream emits
                           // If we have functions declare inputs of Stream<T>, then we end up doing a context search for a stream,
                           // each time that we go to evaluate the function.
                           // Most typically this occurs when the receiver argument is parameterized.
                           // ie - people don't generally declare
                           //    declare extension function something(stream: Stream<T>):Stream<T>
                           // but they do declare:
                           //    declare extension function <T> something(input: T):T
                           // which ends up operating on a stream.
                           // Therefore, unwrap Stream<T> to T as the input.
                           receiver.copy(type = receiver.type.typeParameters().first())
                        } else receiver

                     // Use the unwrappedReceiver, but ignore the resolved parameter (which is also the receiver)
                     listOf(unwrappedReceiver) + parameters.drop(1)
                  } else parameters
                  buildAndResolveTypeArgumentsOrError(function, allParams, targetType, context)

               }
            }
         }
   }

   private fun compileArgument(
      argumentInputContext: ArgumentContext,
      function: Function,
      namespace: Namespace,
      parameter: Parameter,
      parameterType: Type
   ): Either<List<CompilationError>, Accessor> {
      return when {
         argumentInputContext.literal() != null -> LiteralAccessor(
            argumentInputContext.literal().value()
         ).right()

         argumentInputContext.scalarAccessorExpression() != null -> referenceResolver.compileScalarAccessor(
            argumentInputContext.scalarAccessorExpression(),
            parameterType,
         )

         argumentInputContext.fieldReferenceSelector() != null -> referenceResolver.compileFieldReferenceAccessor(
            function,
            argumentInputContext
         )

         argumentInputContext.typeReferenceSelector() != null -> compileTypeReferenceAccessor(
            namespace,
            argumentInputContext
         )

         argumentInputContext.expressionGroup() != null -> {
            compileExpressionGroupParameter(argumentInputContext.expressionGroup())
         }

         else -> argumentInputContext.createInternalError("readFunction parameter accessor not defined for code ${argumentInputContext.source().content}")
      }.flatMap { parameterAccessor ->
         typeChecker.ifAssignableOrErrorList(
            parameterAccessor,
            parameter,
            argumentInputContext
         ) { parameterAccessor }
      }
   }

   internal fun buildFunctionAccessor(
      functionContext: TaxiParser.FunctionCallContext,
      targetType: Type,
      receiver: Expression? = null,
      /**
       * Allows overriding the name of the function.
       * This is needed when a function call is using dot syntax, eg:
       * PersonName.uppercase()
       */
      functionName: String = functionContext.qualifiedName().identifier().text()
   ): Either<List<CompilationError>, FunctionAccessor> {
      val namespace = functionContext.findNamespace()
      return buildFunctionAccessor(
         namespace,
         functionName,
         functionContext,
         functionContext.argumentList()?.argument() ?: emptyList(),
         targetType,
         receiver
      )
   }

   private fun compileExpressionGroupParameter(expressionGroup: TaxiParser.ExpressionGroupContext): Either<List<CompilationError>, Expression> {
      return tokenProcessor.expressionCompiler().compile(expressionGroup)
   }

   private fun compileTypeReferenceAccessor(
      namespace: String,
      parameterContext: TaxiParser.ArgumentContext
   ): Either<List<CompilationError>, TypeReferenceSelector> {
      return tokenProcessor.typeOrError(namespace, parameterContext.typeReferenceSelector().typeReference())
         .map { type ->
            TypeReferenceSelector(type)
         }
   }
}




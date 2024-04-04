package lang.taxi.compiler

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import lang.taxi.*
import lang.taxi.accessors.Accessor
import lang.taxi.accessors.LiteralAccessor
import lang.taxi.expressions.Expression
import lang.taxi.expressions.TypeExpression
import lang.taxi.functions.Function
import lang.taxi.functions.FunctionAccessor
import lang.taxi.types.FieldReferenceSelector
import lang.taxi.types.PrimitiveType
import lang.taxi.types.StreamType
import lang.taxi.types.Type
import lang.taxi.types.TypeArgument
import lang.taxi.types.TypeChecker
import lang.taxi.types.TypeReferenceSelector
import lang.taxi.utils.flattenErrors
import lang.taxi.utils.invertEitherList
import lang.taxi.utils.wrapErrorsInList
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

   fun parseModelAttributeTypeReference(
      modelAttributeReferenceCtx: TaxiParser.ModelAttributeTypeReferenceContext
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
      return tokenProcessor.attemptToLookupSymbolByName(
         namespace,
         functionName,
         functionContext,
         symbolKind = SymbolKind.FUNCTION
      )
         .wrapErrorsInList()
         .flatMap { qualifiedName ->
            tokenProcessor.resolveFunction(qualifiedName, functionContext).flatMap { function ->
               require(function.isDefined) { "Function should have already been compiled before evaluation in a read function expression" }
               typeChecker.assertIsAssignable(function.returnType!!, targetType, functionContext)
                  ?.let { compilationError ->
                     errors.add(compilationError)
                  }
               receiver?.let { receiver ->
                  val firstParam = function.parameters.firstOrNull()
                  // In theory, this isn't possible, as the compiler will catch it earlier. But, belts 'n' braces
                     ?: return@flatMap listOf(
                        CompilationError(
                           functionContext.toCompilationUnit(),
                           "Function ${function.qualifiedName} can not be called as an extension function, as it does not take any params"
                        )
                     )
                        .left()
                  typeChecker.assertIsAssignable(receiver.returnType, firstParam.type, functionContext)
                     ?.let { compilationError ->
                        return@flatMap listOf(compilationError).left()
                     }

               }

               val unparsedParameters = functionContext.argumentList()?.argument() ?: emptyList()
               val parametersOrErrors: Either<List<CompilationError>, List<Accessor>> =
                  unparsedParameters.mapIndexed { parameterIndex, parameterContext ->
                     val declaredParamIndex = if (receiver != null) parameterIndex + 1 else parameterIndex
                     val parameterType = function.getParameterType(declaredParamIndex)
                     val parameterAccessor: Either<List<CompilationError>, Accessor> = when {
                        parameterContext.literal() != null -> LiteralAccessor(
                           parameterContext.literal().value()
                        ).right()

                        parameterContext.scalarAccessorExpression() != null -> referenceResolver.compileScalarAccessor(
                           parameterContext.scalarAccessorExpression(),
                           parameterType,
                        )

                        parameterContext.fieldReferenceSelector() != null -> referenceResolver.compileFieldReferenceAccessor(
                           function,
                           parameterContext
                        )

                        parameterContext.typeReferenceSelector() != null -> compileTypeReferenceAccessor(
                           namespace,
                           parameterContext
                        )

                        parameterContext.expressionGroup() != null -> {
                           compileExpressionGroupParameter(parameterContext.expressionGroup())
                        }

                        else -> TODO("readFunction parameter accessor not defined for code ${functionContext.source().content}")

                     }.flatMap { parameterAccessor ->
                        typeChecker.ifAssignable(
                           parameterAccessor.returnType, parameterType.basePrimitive
                              ?: PrimitiveType.ANY, parameterContext
                        ) {
                           parameterAccessor
                        }.wrapErrorsInList()
                     }
                     parameterAccessor
                  }.invertEitherList()
                     .flattenErrors()
               parametersOrErrors.flatMap { parameters: List<Accessor> ->
                  // If we're invoked as an extension function, we'll be passed a receiver, which
                  // is to be used as the first parameter
                  val allParams = if (receiver != null) {
                     val unwrappedReceiver = if (receiver is TypeExpression && StreamType.isStream(receiver.type) && function.parameters.firstOrNull()?.type is TypeArgument) {
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

                     listOf(unwrappedReceiver) + parameters
                  } else parameters
                  buildAndResolveTypeArgumentsOrError(function, allParams, targetType, functionContext)

               }
            }
         }
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


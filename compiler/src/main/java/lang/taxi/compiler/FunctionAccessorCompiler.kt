package lang.taxi.compiler

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import lang.taxi.*
import lang.taxi.accessors.Accessor
import lang.taxi.accessors.LiteralAccessor
import lang.taxi.expressions.Expression
import lang.taxi.functions.Function
import lang.taxi.functions.FunctionAccessor
import lang.taxi.types.FieldReferenceSelector
import lang.taxi.types.PrimitiveType
import lang.taxi.types.Type
import lang.taxi.types.TypeChecker
import lang.taxi.types.TypeReferenceSelector
import lang.taxi.utils.flattenErrors
import lang.taxi.utils.invertEitherList
import lang.taxi.utils.wrapErrorsInList
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.RuleContext

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

      ): Either<List<CompilationError>, FunctionAccessor> {
      val namespace = functionContext.findNamespace()
      return tokenProcessor.attemptToLookupSymbolByName(
         namespace,
         functionContext.qualifiedName().identifier().text(),
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

               val unparsedParameters = functionContext.argumentList()?.argument() ?: emptyList()
               val parametersOrErrors: Either<List<CompilationError>, List<Accessor>> =
                  unparsedParameters.mapIndexed { parameterIndex, parameterContext ->
                     val parameterType = function.getParameterType(parameterIndex)
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
                  // There used to be view related stuff here - I suspect now thats
                  //deleted, this can be simplified
                  buildAndResolveTypeArgumentsOrError(function, parameters, targetType, functionContext)

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


package lang.taxi.compiler

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import lang.taxi.CompilationError
import lang.taxi.TaxiParser
import lang.taxi.accessors.Accessor
import lang.taxi.accessors.LiteralAccessor
import lang.taxi.expressions.Expression
import lang.taxi.findNamespace
import lang.taxi.functions.Function
import lang.taxi.functions.FunctionAccessor
import lang.taxi.functions.FunctionModifiers
import lang.taxi.source
import lang.taxi.text
import lang.taxi.types.FieldReferenceSelector
import lang.taxi.types.ModelAttributeReferenceSelector
import lang.taxi.types.PrimitiveType
import lang.taxi.types.QualifiedName
import lang.taxi.types.Type
import lang.taxi.types.TypeChecker
import lang.taxi.types.TypeReferenceSelector
import lang.taxi.utils.wrapErrorsInList
import lang.taxi.value
import org.antlr.v4.runtime.RuleContext

interface FunctionParameterReferenceResolver {
   fun compileScalarAccessor(
      expression: TaxiParser.ScalarAccessorExpressionContext,
      targetType: Type = PrimitiveType.ANY
   ): Either<List<CompilationError>, Accessor>

   fun compileFieldReferenceAccessor(
      function: Function,
      parameterContext: TaxiParser.ParameterContext
   ): Either<List<CompilationError>,FieldReferenceSelector>

   fun parseModelAttributeTypeReference(
      modelAttributeReferenceCtx: TaxiParser.ModelAttributeTypeReferenceContext
   ): Either<List<CompilationError>, Expression>
}

class FunctionAccessorCompiler(
   private val tokenProcessor: TokenProcessor,
   private val typeChecker: TypeChecker,
   private val errors: MutableList<CompilationError>,
   private val referenceResolver: FunctionParameterReferenceResolver,
   private val parentContext: RuleContext?
) {
   internal fun buildFunctionAccessor(
      functionContext: TaxiParser.ReadFunctionContext,
      targetType: Type,

      ): Either<List<CompilationError>, FunctionAccessor> {
      val namespace = functionContext.findNamespace()
      return tokenProcessor.attemptToLookupTypeByName(
         namespace,
         functionContext.functionName().qualifiedName().Identifier().text(),
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

               val parameters =
                  functionContext.formalParameterList().parameter().mapIndexed { parameterIndex, parameterContext ->
                     val parameterType = function.getParameterType(parameterIndex)
                     if (parameterContext.modelAttributeTypeReference() == null && parentContext.isInViewContext()) {
                        return@flatMap CompilationError(
                           parameterContext.start,
                           "Only Model Attribute References are  allowed within Views"
                        ).asList().left()
                     }
                     val parameterAccessor: Either<List<CompilationError>, Accessor> = when {
                        parameterContext.literal() != null -> LiteralAccessor(
                           parameterContext.literal().value()
                        ).right()
                        parameterContext.scalarAccessorExpression() != null -> referenceResolver.compileScalarAccessor(
                           parameterContext.scalarAccessorExpression(),
                           parameterType
                        )
                        parameterContext.fieldReferenceSelector() != null -> referenceResolver.compileFieldReferenceAccessor(
                           function,
                           parameterContext
                        )
                        parameterContext.typeReferenceSelector() != null -> compileTypeReferenceAccessor(
                           namespace,
                           parameterContext
                        )
                        parameterContext.modelAttributeTypeReference() != null -> {
                           if (parentContext.isInViewContext()) {
                              referenceResolver.parseModelAttributeTypeReference(
                                 parameterContext.modelAttributeTypeReference()
                              )
                                 .flatMap { (memberSourceType, memberType) ->
                                    ModelAttributeReferenceSelector(memberSourceType, memberType).right()
                                 }
                           } else {
                              CompilationError(
                                 parameterContext.start,
                                 "Model Attribute References are only allowed within Views"
                              ).asList().left()
                           }
                        }
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
                  }.reportAndRemoveErrorList(errors)
               if (function.modifiers.contains(FunctionModifiers.Query) && !functionContext.isInViewContext()) {
                  CompilationError(
                     functionContext.start,
                     "a query function can only referenced from view definitions!"
                  ).asList().left()
               } else {
                  FunctionAccessor.buildAndResolveTypeArguments(function, parameters).right()
               }
            }
         }
   }

   private fun compileExpressionGroupParameter(expressionGroup: TaxiParser.ExpressionGroupContext): Either<List<CompilationError>, Expression> {
      return tokenProcessor.expressionCompiler().compile(expressionGroup)
   }

   private fun compileTypeReferenceAccessor(
      namespace: String,
      parameterContext: TaxiParser.ParameterContext
   ): Either<List<CompilationError>, TypeReferenceSelector> {
      return tokenProcessor.typeOrError(namespace, parameterContext.typeReferenceSelector().typeType()).map { type ->
         TypeReferenceSelector(type)
      }
   }
}

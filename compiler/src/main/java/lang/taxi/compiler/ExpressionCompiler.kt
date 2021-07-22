package lang.taxi.compiler

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import lang.taxi.CompilationError
import lang.taxi.Namespace
import lang.taxi.TaxiParser
import lang.taxi.expressions.Expression
import lang.taxi.expressions.ExpressionGroup
import lang.taxi.expressions.FunctionExpression
import lang.taxi.expressions.LiteralExpression
import lang.taxi.expressions.OperatorExpression
import lang.taxi.expressions.TypeExpression
import lang.taxi.findNamespace
import lang.taxi.functions.Function
import lang.taxi.source
import lang.taxi.toCompilationUnit
import lang.taxi.toCompilationUnits
import lang.taxi.types.Accessor
import lang.taxi.types.FieldReferenceSelector
import lang.taxi.types.FormulaOperator
import lang.taxi.types.LiteralAccessor
import lang.taxi.types.PrimitiveType
import lang.taxi.types.QualifiedName
import lang.taxi.types.Type
import lang.taxi.types.TypeChecker
import lang.taxi.utils.flattenErrors
import lang.taxi.utils.getOrThrow
import lang.taxi.utils.invertEitherList
import lang.taxi.utils.leftOr
import lang.taxi.utils.takeHead
import lang.taxi.value

class ExpressionCompiler(
   private val tokenProcessor: TokenProcessor,
   typeChecker: TypeChecker,
   errors: MutableList<CompilationError>
) : FunctionParameterReferenceResolver {
   private val functionCompiler = FunctionAccessorCompiler(
      tokenProcessor,
      typeChecker,
      errors,
      this,
      null
   )

   fun compile(expressionGroup: TaxiParser.ExpressionGroupContext): Either<List<CompilationError>, out Expression> {
      return when {
         expressionGroup.LPAREN() != null && expressionGroup.RPAREN() != null -> {
            require(expressionGroup.children.size == 3) { "When handling an expression ${expressionGroup.text} expected exactly 3 children, including the parenthesis" }
            // There must be only one child not a bracket
            // ie., ( A + B ) should yeild LPAREN EXPRESSIONGROUP RPAREN
            require(expressionGroup.expressionGroup().size == 1) { "Expected only a single ExpressionGroup inside parenthesis" }
            compile(expressionGroup.expressionGroup(0))
         }
         expressionGroup.children.size == 3 -> parseOperatorExpression(expressionGroup)          // lhs operator rhs
         expressionGroup.expressionGroup().isEmpty() -> compileSingleExpression(expressionGroup)
         else -> error("Unhandled expression group scenario: ${expressionGroup.text}")
      }
   }

   private fun compileSingleExpression(expression: TaxiParser.ExpressionGroupContext): Either<List<CompilationError>, Expression> {
      return when {
         expression.expressionAtom() != null -> compileExpressionAtom(expression.expressionAtom())
         else -> TODO("Unhandled single expression: ${expression.text}")
      }
   }

   private fun compileExpressionAtom(expressionAtom: TaxiParser.ExpressionAtomContext): Either<List<CompilationError>, Expression> {
      return when {
         expressionAtom.typeType() != null ->  parseTypeExpression(expressionAtom.typeType()) /*{
            // At this point the grammar isn't sufficiently strong to know if
            // we've been given a type or a function reference
            val typeType = expressionAtom.typeType()
            tokenProcessor.resolveTypeOrFunction(
               typeType.classOrInterfaceType().text,
               expressionAtom
            )
            TODO()

         } */
         expressionAtom.readFunction() != null -> parseFunctionExpression(expressionAtom.readFunction())
         expressionAtom.literal() != null -> parseLiteralExpression(expressionAtom.literal())
         else -> error("Unhandled atom in expression: ${expressionAtom.text}")
      }
   }

   private fun parseLiteralExpression(literal: TaxiParser.LiteralContext): Either<List<CompilationError>, Expression> {
      return LiteralExpression(LiteralAccessor(literal.value()), literal.toCompilationUnits()).right()
   }

   private fun parseOperatorExpression(expressionGroup: TaxiParser.ExpressionGroupContext): Either<List<CompilationError>, out Expression> {

      val lhs = expressionGroup.expressionGroup(0)?.let { compile(it) }
         ?: error("Expected an expression group at index 0")
      val rhs = expressionGroup.expressionGroup(1)?.let { compile(it) }
         ?: error("Expected an expression group at index 1")
      val operatorSymbol = expressionGroup.children[1]
      val operator = when {
         FormulaOperator.isSymbol(operatorSymbol.text) -> FormulaOperator.forSymbol(operatorSymbol.text).right()
         else -> listOf(
            CompilationError(
               expressionGroup.toCompilationUnit(),
               "${operatorSymbol.text} is not a valid operator"
            )
         ).left()
      }
      val expressionComponents = listOf(lhs, rhs, operator)
      return if (expressionComponents.allValid()) {
         OperatorExpression(
            lhs = lhs.getOrThrow(),
            operator = operator.getOrThrow(),
            rhs = rhs.getOrThrow(),
            compilationUnits = expressionGroup.toCompilationUnits()
         ).right()
      } else {
         // Collect all the errors and bail out.
         expressionComponents.invertEitherList().flattenErrors()
            .leftOr(emptyList())
            .left()
      }

   }

   private fun reduceToOperatorExpression(operatorExpressionParts: List<Any>) {
      if (operatorExpressionParts.size < 3) {
         error("Expected to receive at least 3 components")
      }
      val (lhs, rest) = operatorExpressionParts.takeHead()

      TODO()
   }

   private fun parseNestedExpressionGroup(expressionGroup: List<TaxiParser.ExpressionGroupContext>): Either<List<CompilationError>, out Expression> {
      return expressionGroup.map { compile(it) }
         .invertEitherList()
         .flattenErrors()
         .map { ExpressionGroup(it) }

   }

   private fun parseFunctionExpression(readFunction: TaxiParser.ReadFunctionContext): Either<List<CompilationError>, FunctionExpression> {
// Note: Using ANY as the target type for the function's return type, which effectively disables type checking here.
      // We can improve this later.
     return tokenProcessor.getType(readFunction.findNamespace(), PrimitiveType.ANY.qualifiedName, readFunction)
         .flatMap { targetType ->
            functionCompiler.buildReadFunctionAccessor(readFunction, targetType)
         }.map { functionAccessor ->
            FunctionExpression(functionAccessor,readFunction.toCompilationUnits())
         }
   }

   private fun parseTypeExpression(typeType: TaxiParser.TypeTypeContext): Either<List<CompilationError>, TypeExpression> {
      return tokenProcessor.parseType(typeType.findNamespace(), typeType)
         .map { type -> TypeExpression(type, typeType.toCompilationUnits()) }
   }

   override fun compileScalarAccessor(
      expression: TaxiParser.ScalarAccessorExpressionContext,
      targetType: Type
   ): Either<List<CompilationError>, Accessor> {
      return when {
         expression.jsonPathAccessorDeclaration() != null -> listOf(CompilationError(expression.toCompilationUnit(),"JsonPath accessors are not supported in Expression Types")).left()
         expression.xpathAccessorDeclaration() != null -> listOf(CompilationError(expression.toCompilationUnit(),"XPath accessors are not supported in Expression Types")).left()
         expression.columnDefinition() != null -> listOf(CompilationError(expression.toCompilationUnit(),"Column accessors are not supported in Expression Types")).left()
         expression.conditionalTypeConditionDeclaration() != null -> listOf(CompilationError(expression.toCompilationUnit(),"Conditional Types are  not supported in Expression Types")).left()
         // We could possibly relax ths one, if there's a use case.
         expression.defaultDefinition() != null -> listOf(CompilationError(expression.toCompilationUnit(),"Default values are not supported in Expression Types")).left()
         // Deprecated (I think - not sure what this is)
         expression.readExpression() != null -> listOf(CompilationError(expression.toCompilationUnit(),"ReadExpressions are not supported in Expression Types")).left()
         expression.byFieldSourceExpression() != null -> listOf(CompilationError(expression.toCompilationUnit(),"Field accessors are not supported in Expression Types")).left()

         expression.readFunction() != null -> {
            val functionContext = expression.readFunction()
            functionCompiler.buildReadFunctionAccessor(functionContext, targetType)
         }
         else -> error("Unhandled type of accessor expression at ${expression.source().content}")
      }

   }

   override fun compileFieldReferenceAccessor(
      function: Function,
      parameterContext: TaxiParser.ParameterContext
   ): FieldReferenceSelector {
      TODO("Not yet implemented")
   }

   override fun parseModelAttributeTypeReference(
      namespace: Namespace,
      modelAttributeReferenceCtx: TaxiParser.ModelAttributeTypeReferenceContext
   ): Either<List<CompilationError>, Pair<QualifiedName, Type>> {
      TODO("Not yet implemented")
   }
}

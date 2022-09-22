package lang.taxi.compiler

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.handleErrorWith
import arrow.core.left
import arrow.core.right
import lang.taxi.CompilationError
import lang.taxi.TaxiParser
import lang.taxi.accessors.Accessor
import lang.taxi.accessors.LiteralAccessor
import lang.taxi.expressions.Expression
import lang.taxi.expressions.FieldReferenceExpression
import lang.taxi.expressions.FunctionExpression
import lang.taxi.expressions.LambdaExpression
import lang.taxi.expressions.LiteralExpression
import lang.taxi.expressions.OperatorExpression
import lang.taxi.expressions.TypeExpression
import lang.taxi.findNamespace
import lang.taxi.functions.Function
import lang.taxi.source
import lang.taxi.text
import lang.taxi.toCompilationUnit
import lang.taxi.toCompilationUnits
import lang.taxi.types.Enums
import lang.taxi.types.FieldReferenceSelector
import lang.taxi.types.FormulaOperator
import lang.taxi.types.ModelAttributeReferenceSelector
import lang.taxi.types.PrimitiveType
import lang.taxi.types.QualifiedName
import lang.taxi.types.Type
import lang.taxi.types.TypeChecker
import lang.taxi.utils.flattenErrors
import lang.taxi.utils.getOrThrow
import lang.taxi.utils.invertEitherList
import lang.taxi.utils.leftOr
import lang.taxi.valueOrNullValue
import org.antlr.v4.runtime.ParserRuleContext

class ExpressionCompiler(
   private val tokenProcessor: TokenProcessor,
   typeChecker: TypeChecker,
   errors: MutableList<CompilationError>,
   /**
    * Pass the fieldCompiler when the expression being compiled is within the field of a model / query result.
    * This allows field lookups by name in expressions
    */
   private val fieldCompiler: FieldCompiler? = null
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
         expressionGroup.children.size == 2 && expressionGroup.expressionInputs() != null -> parseLambdaExpression(
            expressionGroup
         )
         expressionGroup.children.size == 3 -> parseOperatorExpression(expressionGroup)          // lhs operator rhs
         expressionGroup.expressionGroup().isEmpty() -> compileSingleExpression(expressionGroup)
         else -> error("Unhandled expression group scenario: ${expressionGroup.text}")
      }
   }

   private fun parseLambdaExpression(lambdaExpression: TaxiParser.ExpressionGroupContext): Either<List<CompilationError>, out Expression> {
      require(lambdaExpression.children.size == 2) { "Expected exactly 2 children in the lambda expression" }
      require(lambdaExpression.expressionGroup().size == 1) { "expected exactly 1 expression group on the rhs of the lambda" }
      return lambdaExpression.expressionInputs()
         .expressionInput().map { expressionInput ->
            tokenProcessor.parseType(expressionInput.findNamespace(), expressionInput.typeType())
         }.invertEitherList().flattenErrors()
         .flatMap { inputs ->
            compile(lambdaExpression.expressionGroup(0)).map { expression ->
               LambdaExpression(inputs, expression, lambdaExpression.toCompilationUnits())
            }
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
         expressionAtom.typeType() != null -> parseTypeExpression(expressionAtom.typeType()) /*{
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
         expressionAtom.fieldReferenceSelector() != null -> parseFieldReferenceSelector(expressionAtom.fieldReferenceSelector())
         expressionAtom.modelAttributeTypeReference() != null -> parseModelAttributeTypeReference(expressionAtom.modelAttributeTypeReference())
         else -> error("Unhandled atom in expression: ${expressionAtom.text}")
      }
   }

   private fun parseFieldReferenceSelector(fieldReferenceSelector: TaxiParser.FieldReferenceSelectorContext): Either<List<CompilationError>, Expression> {
      return requireFieldCompilerIsPresent(fieldReferenceSelector).flatMap {
         val fieldName = fieldReferenceSelector.identifier().text
         fieldCompiler!!.provideField(fieldName, fieldReferenceSelector)
            .map { field ->
               FieldReferenceExpression(
                  FieldReferenceSelector.fromField(field),
                  fieldReferenceSelector.toCompilationUnits()
               )
            }
      }
   }

   private fun parseLiteralExpression(literal: TaxiParser.LiteralContext): Either<List<CompilationError>, Expression> {
      return LiteralExpression(LiteralAccessor(literal.valueOrNullValue()), literal.toCompilationUnits()).right()
   }

   private fun parseOperatorExpression(expressionGroup: TaxiParser.ExpressionGroupContext): Either<List<CompilationError>, out Expression> {

      val lhsOrError = expressionGroup.expressionGroup(0)?.let { compile(it) }
         ?: error("Expected an expression group at index 0")
      val rhsOrError = expressionGroup.expressionGroup(1)?.let { compile(it) }
         ?: error("Expected an expression group at index 1")
      val operatorSymbol = expressionGroup.children[1]
      val operatorOrError = when {
         FormulaOperator.isSymbol(operatorSymbol.text) -> FormulaOperator.forSymbol(operatorSymbol.text).right()
         else -> listOf(
            CompilationError(
               expressionGroup.toCompilationUnit(),
               "${operatorSymbol.text} is not a valid operator"
            )
         ).left()
      }
      val expressionComponents = listOf(lhsOrError, rhsOrError, operatorOrError)
      return if (expressionComponents.allValid()) {
         val lhs = lhsOrError.getOrThrow()
         val operator = operatorOrError.getOrThrow()
         val rhs = rhsOrError.getOrThrow()
         // Don't love this, but we don't have access to the type in the null value at this point.
         // Thereofre, we basically disable operator support checks for null comparisons.
         // If we fix that problem, we can keep the operator checking here
         val isNullCheck = LiteralExpression.isNullExpression(lhs) || LiteralExpression.isNullExpression(rhs)
         val lhsType = lhs.returnType.basePrimitive ?: PrimitiveType.ANY
         val rhsType = rhs.returnType.basePrimitive ?: PrimitiveType.ANY
         when {
            isNullCheck && !operator.supportsNullComparison() -> {
               listOf(
                  CompilationError(
                     expressionGroup.toCompilationUnit(),
                     "Operations with symbol '${operator.symbol}' is not supported when comparing against null"
                  )
               ).left()
            }
            !isNullCheck && !operator.supports(lhsType, rhsType) -> {
               listOf(
                  CompilationError(
                     expressionGroup.toCompilationUnit(),
                     "Operations with symbol '${operator.symbol}' is not supported on types ${lhsType.declaration} and ${rhsType.declaration}"
                  )
               ).left()
            }
            else -> {
               OperatorExpression(
                  lhs = lhs,
                  operator = operator,
                  rhs = rhs,
                  compilationUnits = expressionGroup.toCompilationUnits()
               ).right()
            }
         }
      } else {
         // Collect all the errors and bail out.
         expressionComponents.invertEitherList().flattenErrors()
            .leftOr(emptyList())
            .left()
      }

   }


   private fun parseFunctionExpression(readFunction: TaxiParser.ReadFunctionContext): Either<List<CompilationError>, FunctionExpression> {
// Note: Using ANY as the target type for the function's return type, which effectively disables type checking here.
      // We can improve this later.
      return tokenProcessor.getType(readFunction.findNamespace(), PrimitiveType.ANY.qualifiedName, readFunction)
         .flatMap { targetType ->
            functionCompiler.buildFunctionAccessor(readFunction, targetType)
         }.map { functionAccessor ->
            FunctionExpression(functionAccessor, readFunction.toCompilationUnits())
         }
   }

   private fun parseTypeExpression(typeType: TaxiParser.TypeTypeContext): Either<List<CompilationError>, Expression> {
      return tokenProcessor.parseType(typeType.findNamespace(), typeType)
         .map { type -> TypeExpression(type, typeType.toCompilationUnits()) }
         .handleErrorWith { errors ->
            if (Enums.isPotentialEnumMemberReference(typeType.classOrInterfaceType().identifier().text())) {
               tokenProcessor.resolveEnumMember(typeType.classOrInterfaceType().identifier().text(), typeType)
                  .map { enumMember ->
                     LiteralExpression(
                        LiteralAccessor(enumMember.value, enumMember.enum),
                        typeType.toCompilationUnits()
                     )
                  }
            } else {
               errors.left()
            }

         }
   }

   override fun compileScalarAccessor(
      expression: TaxiParser.ScalarAccessorExpressionContext,
      targetType: Type
   ): Either<List<CompilationError>, Accessor> {
      return when {
         expression.jsonPathAccessorDeclaration() != null ||
            expression.xpathAccessorDeclaration() != null ||
            expression.byFieldSourceExpression() != null ||
            expression.conditionalTypeConditionDeclaration() != null ||
            expression.defaultDefinition() != null ||
            expression.collectionProjectionExpression() != null ||
            expression.columnDefinition() != null -> {
            if (this.fieldCompiler == null) {
               listOf(
                  CompilationError(
                     expression.toCompilationUnit(),
                     "Accessors are not supported in Expression Types outside of model declarations"
                  )
               ).left()
            } else {
               fieldCompiler.compileScalarAccessor(expression, targetType)
            }
         }
         expression.readExpression() != null -> {
            val compiled = compile(expression.readExpression().expressionGroup())
            compiled
         }
         expression.readFunction() != null -> {
            val functionContext = expression.readFunction()
            functionCompiler.buildFunctionAccessor(functionContext, targetType)
         }
         else -> error("Unhandled type of accessor expression at ${expression.source().content}")
      }
   }

   private fun requireFieldCompilerIsPresent(parserContext: ParserRuleContext): Either<List<CompilationError>, Boolean> {
      return if (this.fieldCompiler == null) {
         listOf(
            CompilationError(
               parserContext.start,
               "Cannot use field references when outside the scope of a model"
            )
         ).left()
      } else {
         true.right()
      }
   }

   override fun compileFieldReferenceAccessor(
      function: Function,
      parameterContext: TaxiParser.ParameterContext
   ): Either<List<CompilationError>, FieldReferenceSelector> {
      return requireFieldCompilerIsPresent(parameterContext).flatMap {
         fieldCompiler!!.provideField(parameterContext.fieldReferenceSelector().identifier().text, parameterContext)
            .map { field -> FieldReferenceSelector.fromField(field) }
      }
   }

   override fun parseModelAttributeTypeReference(
      modelAttributeReferenceCtx: TaxiParser.ModelAttributeTypeReferenceContext
   ): Either<List<CompilationError>, ModelAttributeReferenceSelector> {

      val memberSourceTypeType = modelAttributeReferenceCtx.typeType().first()
      val memberTypeType = modelAttributeReferenceCtx.typeType()[1]
      val sourceTypeName = try {
         QualifiedName.from(fieldCompiler!!.lookupTypeByName(memberSourceTypeType)).right()
      } catch (e: Exception) {
         CompilationError(
            modelAttributeReferenceCtx.start,
            "Only Model AttributeReference expressions (SourceType::FieldType) are allowed for views"
         ).asList().left()
      }
      return sourceTypeName.flatMap { memberSourceType ->
         fieldCompiler!!.parseType(modelAttributeReferenceCtx.findNamespace(), memberTypeType).flatMap { memberType ->
            ModelAttributeReferenceSelector(memberSourceType, memberType, modelAttributeReferenceCtx.toCompilationUnits()).right()
         }
      }
      // TODO - This isn't implemented, need to find the original implementation
      return listOf(
         CompilationError(
            modelAttributeReferenceCtx.start, "SourceType::FieldType notation is not permitted here"
         )
      ).left()
   }
}

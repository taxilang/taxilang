package lang.taxi.compiler

import arrow.core.*
import lang.taxi.*
import lang.taxi.TaxiParser.QualifiedNameContext
import lang.taxi.accessors.Accessor
import lang.taxi.accessors.Argument
import lang.taxi.accessors.LiteralAccessor
import lang.taxi.compiler.fields.FieldCompiler
import lang.taxi.expressions.Expression
import lang.taxi.expressions.FieldReferenceExpression
import lang.taxi.expressions.FunctionExpression
import lang.taxi.expressions.LambdaExpression
import lang.taxi.expressions.LiteralExpression
import lang.taxi.expressions.OperatorExpression
import lang.taxi.expressions.TypeExpression
import lang.taxi.functions.Function
import lang.taxi.types.*
import lang.taxi.utils.*
import org.antlr.v4.runtime.ParserRuleContext

class ExpressionCompiler(
   private val tokenProcessor: TokenProcessor,
   private val typeChecker: TypeChecker,
   private val errors: MutableList<CompilationError>,
   /**
    * Pass the fieldCompiler when the expression being compiled is within the field of a model / query result.
    * This allows field lookups by name in expressions
    */
   private val fieldCompiler: FieldCompiler? = null,
   private val scopes: List<Argument> = emptyList()
) : FunctionParameterReferenceResolver {
   private val functionCompiler = FunctionAccessorCompiler(
      tokenProcessor,
      typeChecker,
      errors,
      this,
      null
   )

   fun withParameters(arguments:List<Argument>): ExpressionCompiler {
      // TODO : In future, does it make sense to "nest" these, so that as we add arguments,
      // they form scopes / contexts?
      // For now, everything is flat.
      return ExpressionCompiler(
         tokenProcessor,
         typeChecker,
         errors,
         fieldCompiler,
         scopes + arguments
      )
   }

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
            tokenProcessor.parseType(expressionInput.findNamespace(), expressionInput.typeReference())
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
         expressionAtom.typeReference() != null -> parseTypeExpression(expressionAtom.typeReference()) /*{
            // At this point the grammar isn't sufficiently strong to know if
            // we've been given a type or a function reference
            val typeType = expressionAtom.typeReference()
            tokenProcessor.resolveTypeOrFunction(
               typeType.qualifiedName().text,
               expressionAtom
            )
            TODO()

         } */
         expressionAtom.functionCall() != null -> parseFunctionExpression(expressionAtom.functionCall())
         expressionAtom.literal() != null -> parseLiteralExpression(expressionAtom.literal())
         expressionAtom.fieldReferenceSelector() != null -> parseFieldReferenceSelector(expressionAtom.fieldReferenceSelector())
         expressionAtom.modelAttributeTypeReference() != null -> parseModelAttributeTypeReference(expressionAtom.modelAttributeTypeReference())
         else -> error("Unhandled atom in expression: ${expressionAtom.text}")
      }
   }

   private fun parseFieldReferenceSelector(fieldReferenceSelector: TaxiParser.FieldReferenceSelectorContext): Either<List<CompilationError>, Expression> {
      return requireFieldCompilerIsPresent(fieldReferenceSelector).flatMap {
         val fieldPath = fieldReferenceSelector.qualifiedName().identifier()

         val (firstPathElement, remainingPathElements) = fieldPath.takeHead()

         var error: CompilationMessage? = null
         return fieldCompiler!!.provideField(firstPathElement.text, fieldReferenceSelector)
            .flatMap { field ->
               val fieldSelectors = remainingPathElements
                  .asSequence()
                  .takeWhile { error == null }
                  // Cast to nullable type, as it allows us to return null when an error is thrown
                  .runningFold(FieldReferenceSelector.fromField(field) as FieldReferenceSelector?) { lastField, pathElement ->
                     val lastFieldReturnType = lastField!!.returnType

                     // Check that the type has properties
                     if (lastFieldReturnType !is ObjectType) {
                        error = CompilationError(
                           pathElement.toCompilationUnit(),
                           "${lastFieldReturnType.toQualifiedName().parameterizedName} does not expose properties"
                        )
                        null

                        // Check that the field exists on the type
                     } else if (!lastFieldReturnType.hasField(pathElement.text)) {
                        error = CompilationError(
                           pathElement.toCompilationUnit(),
                           "${lastFieldReturnType.toQualifiedName().parameterizedName} does not have a property ${pathElement.text}"
                        )
                        null
                     } else {
                        FieldReferenceSelector.fromField(lastFieldReturnType.field(pathElement.text))
                     }
                  }
                  .filterNotNull()
                  .toList()

               if (error != null) {
                  listOfNotNull(error).left()
               } else {
                  FieldReferenceExpression(
                     fieldSelectors,
                     fieldReferenceSelector.toCompilationUnits()
                  ).right()
               }

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
      if (expressionComponents.allValid()) {
         val lhs = lhsOrError.getOrThrow()
         val operator = operatorOrError.getOrThrow()
         val rhs = rhsOrError.getOrThrow()
         // Don't love this, but we don't have access to the type in the null value at this point.
         // Thereofre, we basically disable operator support checks for null comparisons.
         // If we fix that problem, we can keep the operator checking here
         val isNullCheck = LiteralExpression.isNullExpression(lhs) || LiteralExpression.isNullExpression(rhs)

         val (coercedLhs, coercedRhs) = TypeCaster.coerceTypesIfRequired(lhs,rhs).getOrHandle { error ->
            return listOf(CompilationError(expressionGroup.toCompilationUnit(), error)).left()
         }

         val lhsType = coercedLhs.returnType.basePrimitive ?: PrimitiveType.ANY
         val rhsType = coercedRhs.returnType.basePrimitive ?: PrimitiveType.ANY

         return when {
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
                  lhs = coercedLhs,
                  operator = operator,
                  rhs = coercedRhs,
                  compilationUnits = expressionGroup.toCompilationUnits()
               ).right()
            }
         }
      } else {
         // Collect all the errors and bail out.
         return expressionComponents.invertEitherList().flattenErrors()
            .leftOr(emptyList())
            .left()
      }

   }


   private fun parseFunctionExpression(readFunction: TaxiParser.FunctionCallContext): Either<List<CompilationError>, FunctionExpression> {
// Note: Using ANY as the target type for the function's return type, which effectively disables type checking here.
      // We can improve this later.
      return tokenProcessor.getType(readFunction.findNamespace(), PrimitiveType.ANY.qualifiedName, readFunction)
         .flatMap { targetType ->
            functionCompiler.buildFunctionAccessor(readFunction, targetType)
         }.map { functionAccessor ->
            FunctionExpression(functionAccessor, readFunction.toCompilationUnits())
         }
   }


   private fun parseTypeExpression(typeType: TaxiParser.TypeReferenceContext): Either<List<CompilationError>, Expression> {
      if (canResolveAsScopePath(typeType.qualifiedName())) {
         return resolveScopePath(typeType.qualifiedName())
      }

      return tokenProcessor.parseType(typeType.findNamespace(), typeType)
         .map { type -> TypeExpression(type, typeType.toCompilationUnits()) }
         .handleErrorWith { errors ->
            if (Enums.isPotentialEnumMemberReference(typeType.qualifiedName().identifier().text())) {
               tokenProcessor.resolveEnumMember(typeType.qualifiedName().identifier().text(), typeType)
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

   fun canResolveAsScopePath(qualifiedName: QualifiedNameContext): Boolean {
      val identifierTokens = qualifiedName.identifier().map { it.text }
      return scopes.any { it.matchesReference(identifierTokens) }
   }

   /**
    * Resolves a path declared using a scope, against the field name.
    * for example:
    *
    * find{ Foo[] } as (foo : Foo) {
    *    thing : foo.bar // <---resolves bar property against foo
    * }
    */
   fun resolveScopePath(qualifiedName: QualifiedNameContext): Either<List<CompilationError>, ArgumentSelector> {
      val identifierTokens = qualifiedName.identifier().map { it.text }
      // if we can resolve it through a scope, do so.
      val resolvedScopeReference = scopes.first { it.matchesReference(identifierTokens) }
      return resolveScopePath(
         resolvedScopeReference,
         resolvedScopeReference.pruneFieldPath(identifierTokens),
         qualifiedName
      ).flatMap { fieldSelectors ->

         ArgumentSelector(
            resolvedScopeReference,
            resolvedScopeReference.pruneFieldSelectors(fieldSelectors),
            qualifiedName.toCompilationUnits()
         ).right()
      }

   }

   /**
    * Maps the full scope path.
    * Callers are responsible for dropping the first path, if
    * that's already handled (ie., if the initial scope is "special" - "this")
    */
   private fun resolveScopePath(
      scope: Argument,
      path: List<String>,
      context: ParserRuleContext
   ): Either<List<CompilationError>, List<FieldReferenceSelector>> {
      val initial = FieldReferenceSelector(scope.name, scope.type)
      // We must return the initial scope, so that it can be consistently dropped
      return if (path.isEmpty()) {
         listOf(initial).right()
      } else {
         return path
            .runningFold(initial.right() as Either<List<CompilationError>, FieldReferenceSelector>) { resolvedType, fieldName ->
            resolvedType.flatMap { selector ->
               val previousType = selector.declaredType
               if (previousType is ObjectType && previousType.hasField(fieldName)) {
                  FieldReferenceSelector(fieldName, previousType.field(fieldName).type).right()
               } else {
                  listOf(
                     CompilationError(
                        context.toCompilationUnit(),
                        "Cannot resolve reference $fieldName against type ${previousType.toQualifiedName().parameterizedName}"
                     )
                  ).left()
               }
            }
         }.invertEitherList().flattenErrors()
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

         expression.expressionGroup() != null -> {
            val compiled = compile(expression.expressionGroup())
            compiled
         }
//         expression.functionCall() != null -> {
//            val functionContext = expression.functionCall()
//            functionCompiler.buildFunctionAccessor(functionContext, targetType)
//         }
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
         fieldCompiler!!.provideField(
            parameterContext.fieldReferenceSelector().qualifiedName().identifier().text(),
            parameterContext
         )
            .map { field -> FieldReferenceSelector.fromField(field) }
      }
   }

   override fun parseModelAttributeTypeReference(
      modelAttributeReferenceCtx: TaxiParser.ModelAttributeTypeReferenceContext
   ): Either<List<CompilationError>, ModelAttributeReferenceSelector> {

      val sourceTypeReference = modelAttributeReferenceCtx.typeReference().first()
      val targetTypeReference = modelAttributeReferenceCtx.typeReference()[1]

      return fieldCompiler!!.typeOrError(sourceTypeReference).flatMap { sourceType ->
         fieldCompiler.typeOrError(targetTypeReference).map { targetType ->
            val returnType = if (modelAttributeReferenceCtx.arrayMarker() != null) {
               ArrayType.of(targetType, targetTypeReference.toCompilationUnit())
            } else {
               targetType
            }
            ModelAttributeReferenceSelector(
               sourceType.toQualifiedName(),
               targetType,
               returnType,
               modelAttributeReferenceCtx.toCompilationUnit()
            )
         }
      }
   }
}

package lang.taxi.compiler

//
//@Deprecated("Use expressions instead, as they support order of precedence much better")
//class LogicalExpressionCompiler(private val tokenProcessor: TokenProcessor) {
//   fun processLogicalExpressionContext(logicalExpressionCtx: TaxiParser.Logical_exprContext): Either<CompilationError, LogicalExpression> {
//      return when (logicalExpressionCtx) {
//         is TaxiParser.ComparisonExpressionContext -> processComparisonExpressionContext(logicalExpressionCtx.comparison_expr())
//         is TaxiParser.LogicalExpressionAndContext -> processLogicalAndContext(logicalExpressionCtx)
//         is TaxiParser.LogicalExpressionOrContext -> processLogicalOrContext(logicalExpressionCtx)
//         is TaxiParser.LogicalEntityContext -> processLogicalEntityContext(logicalExpressionCtx)
//         else -> CompilationError(logicalExpressionCtx.start, "invalid logical expression").left()
//      }
//   }
//
//
//   fun compileLiteralValueAssignment(literal: TaxiParser.LiteralContext): Either<List<CompilationError>, ValueAssignment> {
//      return if (literal.valueOrNull() == null) {
//         NullAssignment.right()
//      } else {
//         LiteralAssignment(literal.value()).right()
//      }
//
//   }
//
//   private fun processComparisonExpressionContext(comparisonExpressionContext: TaxiParser.Comparison_exprContext): Either<CompilationError, LogicalExpression> {
//      val comparisonExpressionContextWithOperator = comparisonExpressionContext as TaxiParser.ComparisonExpressionWithOperatorContext
//      val retVal = comparisonExpressionContextWithOperator.comparison_operand().map { comparisonOperandcontext ->
//         val arithmeticExpression = comparisonOperandcontext.arithmetic_expr() as TaxiParser.ArithmeticExpressionNumericEntityContext
//         when (val numericEntity = arithmeticExpression.numeric_entity()) {
//            is TaxiParser.LiteralConstContext -> {
//               val str = numericEntity.literal().valueOrNull()
//               try {
//                  ConstantEntity(str).right()
//               } catch (e: Exception) {
//                  Either.left(CompilationError(comparisonExpressionContext.start,
//                     "$str must be a valid decimal"))
//
//               }
//
//            }
//            is TaxiParser.NumericVariableContext -> {
//               // Case for:
//               // this.fieldName =
//               val qualifiedName = numericEntity.propertyToParameterConstraintLhs().qualifiedName()
//               // Case for:
//               // Order::OrderSentId =
//               val modelAttributeTypeReference = numericEntity.propertyToParameterConstraintLhs().modelAttributeTypeReference()
//
//               when {
//                  qualifiedName != null -> FieldReferenceEntity(numericEntity.propertyToParameterConstraintLhs().qualifiedName().text).right()
//                  modelAttributeTypeReference != null -> {
//                     if (!comparisonExpressionContext.isInViewContext()) {
//                        Either.left(CompilationError(comparisonExpressionContext.start, "SourceType::FieldType notation is only allowed in view definitions"))
//                     } else {
//                        this
//                           .tokenProcessor
//                           .parseModelAttributeTypeReference(comparisonExpressionContext.findNamespace(), modelAttributeTypeReference)
//                           .flatMap { (memberSourceType, memberType) ->  ModelAttributeFieldReferenceEntity(memberSourceType, memberType)
//                              .right()
//                           }
//                     }
//
//                  }
//                  else -> Either.left(CompilationError(comparisonExpressionContext.start, "invalid numeric entity"))
//               }
//            }
//            else -> Either.left(CompilationError(comparisonExpressionContext.start,
//               "invalid numeric entity"))
//         }
//      }
//
//      if (retVal.size != 2) {
//         return Either.left(CompilationError(comparisonExpressionContext.start,
//            "invalid numeric entity"))
//      } else {
//         val mapped = retVal.map {
//            when (it) {
//               is Either.Right -> {
//                  it.b
//               }
//               is Either.Left -> {
//                  // TODO sort out this mess:
//                  val left = it.a
//                  return if (left is CompilationError) {
//                     left.left()
//                  } else if (left is List<*> && left.size >= 1 && left.first() is CompilationError) {
//                     (left.first() as CompilationError).left()
//                  } else {
//                     Either.left(CompilationError(comparisonExpressionContext.start,
//                        "invalid numeric entity"))
//                  }
//
//               }
//            }
//         }
//         return ComparisonExpression(ComparisonOperator.forSymbol(comparisonExpressionContextWithOperator.comp_operator().text), mapped[0], mapped[1]).right()
//      }
//   }
//
//   private fun processLogicalAndContext(logicalExpressionAndCtx: TaxiParser.LogicalExpressionAndContext): Either<CompilationError, LogicalExpression> {
//      val logicalExpr = logicalExpressionAndCtx.logical_expr()
//      val retVal = logicalExpr.map { processLogicalExpressionContext(it) }
//      if (retVal.size != 2) {
//         return Either.left(CompilationError(logicalExpressionAndCtx.start, "invalid and expression"))
//      }
//
//      val mapped = retVal.map {
//         when (it) {
//            is Either.Right -> {
//               it.b
//            }
//            is Either.Left -> {
//               return it.a.left()
//            }
//         }
//      }
//      return AndExpression(mapped[0], mapped[1]).right()
//   }
//
//   private fun processLogicalOrContext(logicalExpressionCtx: TaxiParser.LogicalExpressionOrContext): Either<CompilationError, LogicalExpression> {
//      val logicalExpr = logicalExpressionCtx.logical_expr()
//      val retVal = logicalExpr.map { processLogicalExpressionContext(it) }
//      if (retVal.size != 2) {
//         return Either.left(CompilationError(logicalExpressionCtx.start, "invalid and expression"))
//      }
//
//      val mapped = retVal.map {
//         when (it) {
//            is Either.Right -> {
//               it.b
//            }
//            is Either.Left -> {
//               return Either.left(CompilationError(logicalExpressionCtx.start, "invalid numeric entity"))
//            }
//         }
//      }
//      return OrExpression(mapped[0], mapped[1]).right()
//   }
//
//   private fun processLogicalEntityContext(logicalExpressionCtx: TaxiParser.LogicalEntityContext): Either<CompilationError, LogicalExpression> {
//      return when (val logicalEntity = logicalExpressionCtx.logical_entity()) {
//         is TaxiParser.LogicalVariableContext -> LogicalVariable(logicalEntity.text).right()
//         is TaxiParser.LogicalConstContext -> LogicalConstant(logicalEntity.TRUE() != null).right()
//         else -> CompilationError(logicalExpressionCtx.start, "invalid logical expression").left()
//      }
//   }
//
//
//}

package lang.taxi.compiler

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import lang.taxi.CompilationError
import lang.taxi.TaxiParser
import lang.taxi.findNamespace
import lang.taxi.types.AndExpression
import lang.taxi.types.ComparisonExpression
import lang.taxi.types.ComparisonOperator
import lang.taxi.types.ConstantEntity
import lang.taxi.types.FieldReferenceEntity
import lang.taxi.types.LiteralAssignment
import lang.taxi.types.LogicalConstant
import lang.taxi.types.LogicalExpression
import lang.taxi.types.LogicalVariable
import lang.taxi.types.NullAssignment
import lang.taxi.types.OrExpression
import lang.taxi.types.ValueAssignment
import lang.taxi.types.ViewFindFieldReferenceAssignment
import lang.taxi.types.ViewFindFieldReferenceEntity
import lang.taxi.value
import lang.taxi.valueOrNull
import org.antlr.v4.runtime.tree.TerminalNode

class LogicalExpressionCompiler(private val tokenProcessor: TokenProcessor) {
   fun processLogicalExpressionContext(logicalExpressionCtx: TaxiParser.Logical_exprContext, forViewField: Boolean = false): Either<CompilationError, LogicalExpression> {
      return when (logicalExpressionCtx) {
         is TaxiParser.ComparisonExpressionContext -> processComparisonExpressionContext(logicalExpressionCtx.comparison_expr(), forViewField)
         is TaxiParser.LogicalExpressionAndContext -> processLogicalAndContext(logicalExpressionCtx, forViewField)
         is TaxiParser.LogicalExpressionOrContext -> processLogicalOrContext(logicalExpressionCtx, forViewField)
         is TaxiParser.LogicalEntityContext -> processLogicalEntityContext(logicalExpressionCtx)
         else -> CompilationError(logicalExpressionCtx.start, "invalid logical expression").left()
      }
   }

   fun toViewFindFieldReferenceAssignment(identifiers: List<TerminalNode>, ctx: TaxiParser.CaseFieldReferenceAssignmentContext): Either<List<CompilationError>, ViewFindFieldReferenceAssignment> {
      if (identifiers.size != 2) {
         return listOf(CompilationError(ctx.start, "Should be SourceType.FieldType")).left()
      }
     return this.tokenProcessor.getType(ctx.findNamespace(), identifiers.first().text, ctx)
         .flatMap { sourceType ->
            this.tokenProcessor.getType(ctx.findNamespace(), identifiers[1].text, ctx)
               .flatMap { fieldType ->
                  ViewFindFieldReferenceAssignment(sourceType, fieldType).right()
               }.mapLeft { it}
         }.mapLeft { it }
   }

   fun compileLiteralValueAssignment(literal: TaxiParser.LiteralContext): Either<List<CompilationError>, ValueAssignment> {
      return if (literal.valueOrNull() == null) {
         NullAssignment.right()
      } else {
         LiteralAssignment(literal.value()).right()
      }

   }

   private fun processComparisonExpressionContext(comparisonExpressionContext: TaxiParser.Comparison_exprContext,
                                                  forViewField: Boolean = false): Either<CompilationError, LogicalExpression> {
      val comparisonExpressionContextWithOperator = comparisonExpressionContext as TaxiParser.ComparisonExpressionWithOperatorContext
      val retVal = comparisonExpressionContextWithOperator.comparison_operand().map { comparisonOperandcontext ->
         val arithmeticExpression = comparisonOperandcontext.arithmetic_expr() as TaxiParser.ArithmeticExpressionNumericEntityContext
         when (val numericEntity = arithmeticExpression.numeric_entity()) {
            is TaxiParser.LiteralConstContext -> {
               val str = numericEntity.literal().valueOrNull()
               try {
                  ConstantEntity(str).right()
               } catch (e: Exception) {
                  Either.left(CompilationError(comparisonExpressionContext.start,
                     "$str must be a valid decimal"))

               }

            }
            is TaxiParser.NumericVariableContext -> {
               if (forViewField) {
                  val identifiers = numericEntity.propertyToParameterConstraintLhs().qualifiedName().Identifier()
                  if (identifiers.size != 2) {
                     return CompilationError(comparisonExpressionContext.start, "Should be SourceType.FieldType").left()
                  }

                  this.tokenProcessor.getType(comparisonExpressionContext.findNamespace(), identifiers.first().text, comparisonExpressionContext)
                     .flatMap { sourceType ->
                        this.tokenProcessor.getType(comparisonExpressionContext.findNamespace(), identifiers[1].text, comparisonExpressionContext)
                           .flatMap { fieldType ->
                              ViewFindFieldReferenceEntity(sourceType, fieldType).right()
                           }.mapLeft { it }
                     }.mapLeft { it }
               } else {
                  FieldReferenceEntity(numericEntity.propertyToParameterConstraintLhs().qualifiedName().text).right()
               }
            }
            else -> Either.left(CompilationError(comparisonExpressionContext.start,
               "invalid numeric entity"))
         }
      }

      if (retVal.size != 2) {
         return Either.left(CompilationError(comparisonExpressionContext.start,
            "invalid numeric entity"))
      } else {
         val mapped = retVal.map {
            when (it) {
               is Either.Right -> {
                  it.b
               }
               is Either.Left -> {
                  return Either.left(CompilationError(comparisonExpressionContext.start,
                     "invalid numeric entity"))
               }
            }
         }
         return ComparisonExpression(ComparisonOperator.forSymbol(comparisonExpressionContextWithOperator.comp_operator().text), mapped[0], mapped[1]).right()
      }
   }

   private fun processLogicalAndContext(logicalExpressionAndCtx: TaxiParser.LogicalExpressionAndContext, forViewField: Boolean = false): Either<CompilationError, LogicalExpression> {
      val logicalExpr = logicalExpressionAndCtx.logical_expr()
      val retVal = logicalExpr.map { processLogicalExpressionContext(it, forViewField) }
      if (retVal.size != 2) {
         return Either.left(CompilationError(logicalExpressionAndCtx.start, "invalid and expression"))
      }

      val mapped = retVal.map {
         when (it) {
            is Either.Right -> {
               it.b
            }
            is Either.Left -> {
               return Either.left(CompilationError(logicalExpressionAndCtx.start, "invalid numeric entity"))
            }
         }
      }
      return AndExpression(mapped[0], mapped[1]).right()
   }

   private fun processLogicalOrContext(logicalExpressionCtx: TaxiParser.LogicalExpressionOrContext, forViewField: Boolean = false): Either<CompilationError, LogicalExpression> {
      val logicalExpr = logicalExpressionCtx.logical_expr()
      val retVal = logicalExpr.map { processLogicalExpressionContext(it, forViewField) }
      if (retVal.size != 2) {
         return Either.left(CompilationError(logicalExpressionCtx.start, "invalid and expression"))
      }

      val mapped = retVal.map {
         when (it) {
            is Either.Right -> {
               it.b
            }
            is Either.Left -> {
               return Either.left(CompilationError(logicalExpressionCtx.start, "invalid numeric entity"))
            }
         }
      }
      return OrExpression(mapped[0], mapped[1]).right()
   }

   private fun processLogicalEntityContext(logicalExpressionCtx: TaxiParser.LogicalEntityContext): Either<CompilationError, LogicalExpression> {
      return when (val logicalEntity = logicalExpressionCtx.logical_entity()) {
         is TaxiParser.LogicalVariableContext -> LogicalVariable(logicalEntity.text).right()
         is TaxiParser.LogicalConstContext -> LogicalConstant(logicalEntity.TRUE() != null).right()
         else -> CompilationError(logicalExpressionCtx.start, "invalid logical expression").left()
      }
   }


}

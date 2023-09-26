package lang.taxi.compiler

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import lang.taxi.*
import lang.taxi.accessors.LiteralAccessor
import lang.taxi.accessors.NullValue
import lang.taxi.compiler.fields.FieldCompiler
import lang.taxi.expressions.Expression
import lang.taxi.expressions.LiteralExpression
import lang.taxi.types.AssignmentExpression
import lang.taxi.types.ElseMatchExpression
import lang.taxi.types.InlineAssignmentExpression
import lang.taxi.types.Type
import lang.taxi.types.WhenCaseBlock
import lang.taxi.types.WhenExpression
import lang.taxi.utils.flattenErrors
import lang.taxi.utils.invertEitherList
import lang.taxi.utils.wrapErrorsInList
import org.antlr.v4.runtime.ParserRuleContext

class WhenBlockCompiler internal constructor(
   private val compiler: FieldCompiler,
   private val expressionCompiler: ExpressionCompiler
) {
   private val typeChecker = compiler.typeChecker

   fun compileWhenCondition(
      whenBlock: TaxiParser.WhenBlockContext,
      assignmentTargetType: Type
   ): Either<List<CompilationError>, WhenExpression> {
      return when {
         whenBlock.expressionGroup() != null -> expressionCompiler.compile(whenBlock.expressionGroup())

         // These are cases where there isn't an explicit when (matchClause),
         // but are instead when {
         //    someCondition -> ...
         // }
         // Therefore, we're essentially matching against true
         whenBlock.whenCaseDeclaration() != null -> {
            LiteralExpression(LiteralAccessor(true), whenBlock.toCompilationUnits()).right()
         }

         else -> error("Unhandled when condition: ${whenBlock.text}")
      }.flatMap { selectorExpression ->
         val cases = compileWhenCases(
            whenBlock.whenCaseDeclaration(),
            selectorExpression.returnType,
            assignmentTargetType
         )
            .invertEitherList().flattenErrors()
            .map { cases ->
               WhenExpression(selectorExpression, cases, whenBlock.toCompilationUnits())
            }
         cases
      }
   }

   private fun compileWhenCases(
      conditionalTypeWhenCaseDeclaration: List<TaxiParser.WhenCaseDeclarationContext>,
      whenSelectorType: Type,
      assignmentTargetType: Type
   ): List<Either<List<CompilationError>, WhenCaseBlock>> {
      return conditionalTypeWhenCaseDeclaration.map { compileWhenCase(it, whenSelectorType, assignmentTargetType) }
   }

   private fun compileWhenCase(
      whenCase: TaxiParser.WhenCaseDeclarationContext,
      whenClauseSelectorType: Type,
      assignmentTargetType: Type
   ): Either<List<CompilationError>, WhenCaseBlock> {
      return compileMatchExpression(whenCase.caseDeclarationMatchExpression())
         .flatMap { matchExpression ->
            typeChecker.ifAssignable(matchExpression.returnType, whenClauseSelectorType, whenCase) { matchExpression }
               .wrapErrorsInList()
         }
         .flatMap { matchExpression ->
            val assignments: Either<List<CompilationError>, List<AssignmentExpression>> = when {
               // by xpath(), column(), jsonPath() etc...
               whenCase.expressionGroup() != null -> expressionCompiler.compile(
                  whenCase.expressionGroup(),
                  whenClauseSelectorType
               )
                  .map { expression ->
                     if (expression is LiteralExpression && expression.literal.value is NullValue) {
                        // Enrich the null with type information
                        LiteralExpression(LiteralAccessor.typedNull(assignmentTargetType), expression.compilationUnits)
                     } else {
                        expression
                     }
                  }
                  .flatMap { expression ->
                     typeCheckExpressionAssignment(
                        expression,
                        assignmentTargetType,
                        whenCase.expressionGroup()
                     )
                  }
                  .map { listOf(InlineAssignmentExpression(it)) }

               whenCase.scalarAccessorExpression() != null -> {
                  compileScalarFieldAssignment(whenCase.scalarAccessorExpression(), assignmentTargetType)
                     .flatMap { expression ->
                        typeChecker.ifAssignable(
                           expression.returnType,
                           assignmentTargetType,
                           whenCase.scalarAccessorExpression()
                        ) {
                           listOf(expression)
                        }.wrapErrorsInList()
                     }
               }

               else -> error("Unhandled when case branch")
            }
            assignments.map { assignmentExpressions ->
               WhenCaseBlock(matchExpression, assignmentExpressions)
            }
         }
   }

   private fun <T : Expression> typeCheckExpressionAssignment(
      expression: T,
      assignmentTargetType: Type,
      context: ParserRuleContext
   ): Either<List<CompilationMessage>, T> {
      return expression.strictReturnType
         .mapLeft { CompilationMessage(context.start, it) }
         .flatMap { expressionReturnType ->
            typeChecker.ifAssignable(expressionReturnType, assignmentTargetType, context) {
               expression
            }
         }
         .wrapErrorsInList()
   }

   //
   private fun compileScalarFieldAssignment(
      scalarAssigningDeclaration: TaxiParser.ScalarAccessorExpressionContext,
      whenClauseSelectorType: Type
   ): Either<List<CompilationError>, AssignmentExpression> {
      return compiler.compileScalarAccessor(scalarAssigningDeclaration, targetType = whenClauseSelectorType)
         .map { accessor -> InlineAssignmentExpression(accessor) }
   }

   private fun compileMatchExpression(caseDeclarationMatchExpression: TaxiParser.CaseDeclarationMatchExpressionContext): Either<List<CompilationError>, Expression> {
      return when {
         caseDeclarationMatchExpression.expressionGroup() != null -> expressionCompiler.compile(
            caseDeclarationMatchExpression.expressionGroup(),
         )

         caseDeclarationMatchExpression.K_Else() != null -> ElseMatchExpression.right()
         else -> error("Unhandled case match expression: ${caseDeclarationMatchExpression.text}")
      }
   }

}


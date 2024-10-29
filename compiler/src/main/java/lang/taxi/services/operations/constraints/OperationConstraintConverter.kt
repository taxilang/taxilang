package lang.taxi.services.operations.constraints

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import lang.taxi.CompilationError
import lang.taxi.TaxiParser
import lang.taxi.compiler.ExpressionCompiler
import lang.taxi.expressions.Expression
import lang.taxi.expressions.OperatorExpression
import lang.taxi.expressions.TypeExpression
import lang.taxi.services.Operation
import lang.taxi.services.Parameter
import lang.taxi.toCompilationUnits
import lang.taxi.types.ArgumentSelector
import lang.taxi.types.FormulaOperator
import lang.taxi.utils.createCompilationError
import lang.taxi.utils.createInternalError
import kotlin.math.exp

class OperationConstraintConverter(
   private val expression: TaxiParser.ExpressionGroupContext?,
   private val returnValueOriginExpressionContext: TaxiParser.OperationReturnValueOriginExpressionContext?,
   private val expressionCompiler: ExpressionCompiler,
   private val hasReturnTypeSpreadOperator: Boolean,
   private val parameters: List<Parameter>,
   private val context: TaxiParser.OperationReturnTypeContext
) {
   private fun collectParametersPresent(constraints: List<Constraint>): List<Parameter> {
      return constraints.flatMap { collectParametersPresent(it) }
   }
   private fun collectParametersPresent(constraint: Constraint): List<Parameter> {
      require(constraint is ExpressionConstraint) { "Expected ExpressionConstraint but got ${constraint::class.simpleName}."}
      return collectParametersPresent(constraint.expression)
   }

   private fun collectParametersPresent(expression: Expression): List<Parameter> {
      return when (expression) {
         is OperatorExpression -> {
            collectParametersPresent(expression.lhs) + collectParametersPresent(expression.rhs)
         }
         is ArgumentSelector -> {
            listOf(expression.scope as Parameter)
         }
         // TODO : Anything else here?
         else -> emptyList()
      }
   }

   fun constraints(): Either<List<CompilationError>, List<Constraint>> {

      val errors = mutableListOf<CompilationError>()
      val constraints = mutableListOf<Constraint>()

      // Compile the returnValueOriginExpressionContext if present
      if (returnValueOriginExpressionContext != null) {
         ReturnValueDerivedFromInputConstraintProvider
            .build(returnValueOriginExpressionContext)
            .getOrElse {
               errors.addAll(it)
               null
            }?.let { constraints.add(it) }
      }

      // If there's nothing to do, bail early.
      if (expression == null && !hasReturnTypeSpreadOperator) {
         return constraints.right()
      }

      val expressionConstraints = expression?.let {
         ExpressionConstraintBuilder.build(expression, expressionCompiler)
      } ?: emptyList<Constraint>().right()

      val expression = if (hasReturnTypeSpreadOperator) {
         compileSpreadOperatorExpression(expressionConstraints)
      } else {
        expressionConstraints
      }

      expression.getOrElse {
         errors.addAll(it)
         null
      }?.let { constraints.addAll(it) }

      return if (errors.isNotEmpty()) {
         errors.left()
      } else {
         constraints.right()
      }
   }

   private fun compileSpreadOperatorExpression(expressionConstraints: Either<List<CompilationError>, List<Constraint>>) =
      expressionConstraints.flatMap { constraints ->
         val usedParameters = collectParametersPresent(constraints)
         val unusedParametersExpression = parameters.filter { !usedParameters.contains(it) }
            .map { parameter ->
               OperatorExpression(
                  lhs = TypeExpression(
                     parameter.type,
                     constraints = emptyList(),
                     compilationUnits = context.toCompilationUnits()
                  ),
                  operator = FormulaOperator.Equal,
                  rhs = ArgumentSelector(
                     scope = parameter,
                     selectors = emptyList(),
                     compilationUnits = context.toCompilationUnits()
                  ),
                  compilationUnits = context.toCompilationUnits()
               )
            }
            .reduce { acc, operatorExpression ->
               OperatorExpression(
                  lhs = acc,
                  operator = FormulaOperator.LogicalAnd,
                  rhs = operatorExpression,
                  compilationUnits = context.toCompilationUnits()
               )
            }
         when (constraints.size) {
            0 -> unusedParametersExpression.right()
            1 -> {
               val constraint = constraints.single()
               if (constraint !is ExpressionConstraint) {
                  context.createInternalError("Expected to receive a ExpressionConstraint, but got ${constraint::class.simpleName}")
               } else {
                  OperatorExpression(
                     lhs = constraint.expression,
                     operator = FormulaOperator.LogicalAnd,
                     rhs = unusedParametersExpression,
                     compilationUnits = context.toCompilationUnits()
                  )
                     .right()
               }
            }

            else -> {
               context.createInternalError("Expected to receive at most 1 constraint, but found ${constraints.size}")
            }
         }
      }.map { spreadOperatorExpression ->
         listOf(ExpressionConstraint(spreadOperatorExpression))
      }
}

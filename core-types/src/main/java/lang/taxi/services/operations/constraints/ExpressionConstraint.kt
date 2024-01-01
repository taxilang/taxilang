package lang.taxi.services.operations.constraints

import lang.taxi.ImmutableEquality
import lang.taxi.expressions.Expression
import lang.taxi.expressions.OperatorExpression
import lang.taxi.types.CompilationUnit

/**
 * A Simple wrapper around Expressions to make them implement the Constraint contract
 */
class ExpressionConstraint(val expression: Expression) : Constraint {
   private val equality = ImmutableEquality(this, ExpressionConstraint::expression)
   override fun hashCode(): Int = equality.hash()
   override fun equals(other: Any?) = equality.isEqualTo(other)

   override fun asTaxi(): String = expression.asTaxi()

   override val compilationUnits: List<CompilationUnit> = expression.compilationUnits
}



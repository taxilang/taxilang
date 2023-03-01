package lang.taxi.services.operations.constraints

import lang.taxi.expressions.Expression
import lang.taxi.types.CompilationUnit

/**
 * A Simple wrapper around Expressions to make them implement the Constraint contract
 */
class ExpressionConstraint(val expression: Expression) : Constraint {
   override fun asTaxi(): String = expression.asTaxi()

   override val compilationUnits: List<CompilationUnit> = expression.compilationUnits
}



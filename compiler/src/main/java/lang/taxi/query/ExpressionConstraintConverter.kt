package lang.taxi.query

import lang.taxi.Operator
import lang.taxi.TaxiDocument
import lang.taxi.expressions.LiteralExpression
import lang.taxi.expressions.OperatorExpression
import lang.taxi.expressions.TypeExpression
import lang.taxi.services.operations.constraints.*
import lang.taxi.types.FormulaOperator

/**
 * ExpressionConstraints are the preferred model for describing constraints.
 * However, they're late-to-the-party, and we have PropertyToParameterConstraint support everywhere.
 *
 * While we phase it out, need to provide backwards compatibility.
 */
fun ExpressionConstraint.convertToPropertyConstraint(): PropertyToParameterConstraint {
   require(this.expression is OperatorExpression) {"Only operator expressions can be downgraded to PropertyToParameterConstraint.  Got ${expression::class.simpleName}"}
   val expression = this.expression as OperatorExpression
   val propertyIdentifier = when (val lhs = expression.lhs) {
      is TypeExpression -> PropertyTypeIdentifier(lhs.returnType.toQualifiedName())
      else -> TODO("Support for ${lhs::class.simpleName} on LHS is not yet implemented")
   } as PropertyIdentifier

   val valueExpression = when (val rhs = expression.rhs) {
      is LiteralExpression -> ConstantValueExpression(rhs.value)
      else -> TODO("Support for ${rhs::class.simpleName} on RHS is not yet implemented")
   } as ValueExpression
   return PropertyToParameterConstraint(
      propertyIdentifier,
      Operator.parse(expression.operator.symbol),
      valueExpression,
      this.compilationUnits
   )
}


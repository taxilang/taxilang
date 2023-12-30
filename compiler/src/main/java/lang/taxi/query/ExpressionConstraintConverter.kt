package lang.taxi.query

import lang.taxi.Operator
import lang.taxi.expressions.LiteralExpression
import lang.taxi.expressions.OperatorExpression
import lang.taxi.expressions.TypeExpression
import lang.taxi.services.operations.constraints.*
import lang.taxi.types.ArgumentSelector

/**
 * ExpressionConstraints are the preferred model for describing constraints.
 * However, they're late-to-the-party, and we have PropertyToParameterConstraint support everywhere.
 *
 * While we phase it out, need to provide backwards compatibility.
 */
fun ExpressionConstraint.convertToPropertyConstraint(): List<PropertyToParameterConstraint> {
   require(this.expression is OperatorExpression) {"Only operator expressions can be downgraded to PropertyToParameterConstraint.  Got ${expression::class.simpleName}"}
   val expression = this.expression as OperatorExpression
   return expression.convertToPropertyConstraint()
}

private fun OperatorExpression.convertToPropertyConstraint():List<PropertyToParameterConstraint> {
   if (lhs is OperatorExpression && rhs is OperatorExpression) {
      return (lhs as OperatorExpression).convertToPropertyConstraint() + (rhs as OperatorExpression).convertToPropertyConstraint()
   }
   val propertyIdentifier = when (val lhs = this.lhs) {
      is TypeExpression -> PropertyTypeIdentifier(lhs.returnType)
      is ArgumentSelector -> {
         PropertyFieldNameIdentifier(lhs.path)
      }
      else -> TODO("Support for ${lhs::class.simpleName} on LHS is not yet implemented")
   } as PropertyIdentifier

   val valueExpression = when (val rhs = this.rhs) {
      is LiteralExpression -> ConstantValueExpression(rhs.value)
      is ArgumentSelector -> ArgumentExpression(rhs)
      else -> TODO("Support for ${rhs::class.simpleName} on RHS is not yet implemented")
   } as ValueExpression
   return listOf(PropertyToParameterConstraint(
      propertyIdentifier,
      Operator.parse(this.operator.symbol),
      valueExpression,
      this.compilationUnits
   ))
}

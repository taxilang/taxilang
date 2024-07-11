package lang.taxi.query

import lang.taxi.Operator
import lang.taxi.expressions.LiteralExpression
import lang.taxi.expressions.OperatorExpression
import lang.taxi.expressions.TypeExpression
import lang.taxi.services.operations.constraints.*
import lang.taxi.types.ArgumentSelector
import lang.taxi.types.ModelAttributeReferenceSelector

/**
 * ExpressionConstraints are the preferred model for describing constraints.
 * However, they're late-to-the-party, and we have PropertyToParameterConstraint support everywhere.
 *
 * While we phase it out, need to provide backwards compatibility.
 */
fun ExpressionConstraint.convertToConstraint(): List<Constraint> {
   require(this.expression is OperatorExpression) {"Only operator expressions can be downgraded to PropertyToParameterConstraint.  Got ${expression::class.simpleName}"}
   val expression = this.expression as OperatorExpression
   return expression.convertToConstraint()
}

private fun OperatorExpression.convertToConstraint():List<Constraint> {
   if (lhs is OperatorExpression && rhs is OperatorExpression) {
      return (lhs as OperatorExpression).convertToConstraint() +
             ExpressionConstraint(this) +
             (rhs as OperatorExpression).convertToConstraint()
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
      is ModelAttributeReferenceSelector -> {
         if (rhs.argumentSelector != null) {
            ArgumentExpression(rhs.argumentSelector!!)
         } else {
            TODO("Support for Model Attribute selectors (A::B) without an argument scope is not implemented")
         }
      }
      else -> TODO("Support for ${rhs::class.simpleName} on RHS is not yet implemented")
   } as ValueExpression
   return listOf(PropertyToParameterConstraint(
      propertyIdentifier,
      Operator.parse(this.operator.symbol),
      valueExpression,
      this.compilationUnits
   ))
}

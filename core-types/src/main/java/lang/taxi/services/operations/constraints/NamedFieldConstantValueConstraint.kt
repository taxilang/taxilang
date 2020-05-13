package lang.taxi.services.operations.constraints

import lang.taxi.Operator
import lang.taxi.types.AttributePath
import lang.taxi.types.QualifiedName


/**
 * Indicates that an attribute of a parameter (which is an Object type)
 * must have a constant value
 * eg:
 * Given Money(amount:Decimal, currency:String),
 * could express that Money.currency must have a value of 'GBP'
 *
 * Note - it's recommended to use TypedFieldConstantValueConstraint,
 * as that allows expressions which aren't bound to field name contracts,
 * making them more polymorphic
 */
data class NamedFieldConstantValueConstraint(
   // TODO : It may be that we're not always adding constraints
   // to Object types (types with fields).  When I hit a scenario like that,
   // relax this constraint to make it optional, and update accordingly.
   val fieldName: String,
   val operator:Operator = Operator.EQUAL,
   val expectedValue: Any) : Constraint {
   override fun asTaxi(): String = "$fieldName = \"$expectedValue\""
}


data class PropertyToParameterConstraint(
   val propertyIdentifier: PropertyIdentifier,
   val operator: Operator = Operator.EQUAL,
   val expectedValue: ValueExpression
) : Constraint {
   override fun asTaxi(): kotlin.String {
      TODO("Not yet implemented")
   }
}

sealed class PropertyIdentifier
// Identifies a property by it's name
data class PropertyFieldNameIdentifier(val name:AttributePath) : PropertyIdentifier()
data class PropertyTypeIdentifier(val type: QualifiedName) : PropertyIdentifier()

sealed class ValueExpression
data class ConstantValueExpression(val value:Any):ValueExpression()
data class RelativeValueExpression(val path:AttributePath):ValueExpression()

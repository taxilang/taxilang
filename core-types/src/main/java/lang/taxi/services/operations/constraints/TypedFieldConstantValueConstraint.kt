package lang.taxi.services.operations.constraints

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
data class TypedFieldConstantValueConstraint(
   // TODO : It may be that we're not always adding constraints
   // to Object types (types with fields).  When I hit a scenario like that,
   // relax this constraint to make it optional, and update accordingly.
   val qualifiedName: QualifiedName, val expectedValue: Any) : Constraint {
   override fun asTaxi(): String = "${qualifiedName.parameterizedName} = \"$expectedValue\""
}

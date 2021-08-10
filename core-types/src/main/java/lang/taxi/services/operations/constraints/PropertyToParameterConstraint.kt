package lang.taxi.services.operations.constraints

import lang.taxi.ImmutableEquality
import lang.taxi.Operator
import lang.taxi.types.AttributePath
import lang.taxi.types.CompilationUnit
import lang.taxi.types.FilterExpression
import lang.taxi.types.QualifiedName
import lang.taxi.utils.prependIfAbsent
import lang.taxi.utils.quotedIfNecessary


/**
 * Indicates that an attribute of a parameter (which is an Object type)
 * must have a relationship to a specified value.
 *
 * The property may be identified either by it's type (preferred), or it's field name.
 * Use of field names is discouraged, as it leads to tightly coupled models.
 * eg:
 * Given Money(amount:Decimal, currency:String),
 * could express that Money.currency must have a value of 'GBP'
 *
 * Note - it's recommended to use TypedFieldConstantValueConstraint,
 * as that allows expressions which aren't bound to field name contracts,
 * making them more polymorphic
 */
open class PropertyToParameterConstraint(
   val propertyIdentifier: PropertyIdentifier,
   val operator: Operator = Operator.EQUAL,
   val expectedValue: ValueExpression,
   override val compilationUnits: List<CompilationUnit>
) : Constraint, FilterExpression {
   override fun asTaxi(): String {
      return "${propertyIdentifier.taxi} ${operator.symbol} ${expectedValue.taxi}"
   }

   private val equality = ImmutableEquality(this, PropertyToParameterConstraint::propertyIdentifier, PropertyToParameterConstraint::operator, PropertyToParameterConstraint::expectedValue)

   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()

}

sealed class PropertyIdentifier(val description: String, val taxi: String) {
}

// Identifies a property by it's name
data class PropertyFieldNameIdentifier(val name: AttributePath) : PropertyIdentifier("field ${name.path}", name.path.prependIfAbsent("this.")) {
   constructor(fieldName: String) : this(AttributePath.from(fieldName))
}

// TODO : Syntax here is still up for discussion.  See OperationContextSpec
data class PropertyTypeIdentifier(val type: QualifiedName) : PropertyIdentifier("type ${type.parameterizedName}", type.parameterizedName)

sealed class ValueExpression(val taxi: String)

// TODO : This won't work with numbers - but neither does the parsing.  Need to fix that.
data class ConstantValueExpression(val value: Any) : ValueExpression(value.quotedIfNecessary())
data class RelativeValueExpression(val path: AttributePath) : ValueExpression(path.path) {
   constructor(attributeName: String) : this(AttributePath.from(attributeName))
}

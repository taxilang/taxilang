package lang.taxi.services.operations.constraints

import lang.taxi.Operator
import lang.taxi.services.ParamName
import lang.taxi.types.QualifiedName

/**
 * Indicates that an attribute (identified by type) will be returned satisfying
 * a constraint relative to a value provided by a parameter (ie., an input on a function)
 *
 * eg:
 * operation setCcy(requiredCcy:CurrencySymbol):Money(CurrencySymbol = requiredCcy)
 *
 * It is generally preferred to use TypedFieldValueFromParameterConstraint over NamedFieldValueFromParameterConstraint,
 * as it does not impose a field-name based contract on the response, making it more polymorphic
 *
 */
data class TypedFieldValueFromParameterConstraint(val memberTypeName: QualifiedName, val parameterName: ParamName, val operator: Operator = Operator.EQUAL) : Constraint {
   override fun asTaxi(): String = "$memberTypeName ${operator.symbol} $parameterName"
}

package lang.taxi.services.operations.constraints

import lang.taxi.Operator
import lang.taxi.types.AttributePath

/**
 * Indicates that an attribute (identified by it's field name) will be returned satisfying
 * a constraint relative to a value provided by a parameter (ie., an input on a function)
 *
 * eg:
 * operation setCcy(requiredCcy:CurrencySymbol):Money(this.currencySymbol = requiredCcy)
 *
 * It is generally preferred to use TypedFieldValueFromParameterConstraint, as it does not impose
 * a field-name based contract on the response, making it more polymorphic
 *
 */
data class NamedFieldValueFromParameterConstraint(val parameterName: String, val attributePath: AttributePath, val operator:Operator = Operator.EQUAL) : Constraint {
   override fun asTaxi(): String = "this.${attributePath.path} ${operator.symbol} $parameterName"
}

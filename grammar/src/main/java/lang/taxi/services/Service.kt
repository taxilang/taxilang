package lang.taxi.services

import lang.taxi.Annotatable
import lang.taxi.Named
import lang.taxi.Type
import lang.taxi.types.Annotation

data class Parameter(override val annotations: List<Annotation>, val type: Type, val name: String?, val constraints: List<Constraint>) : Annotatable

data class Operation(val name: String, override val annotations: List<Annotation>, val parameters: List<Parameter>, val returnType: Type, val contract: OperationContract? = null) : Annotatable
data class Service(override val qualifiedName: String, val operations: List<Operation>, override val annotations: List<Annotation>) : Annotatable, Named {
    fun operation(name: String): Operation {
        return this.operations.first { it.name == name }
    }
}


/**
 * Indicates that an attribute of a parameter (which is an Object type)
 * must have a constant value
 * eg:
 * Given Money(amount:Decimal, currency:String),
 * could express that Money.currency must have a value of 'GBP'
 */
data class AttributeConstantValueConstraint(
        // TODO : It may be that we're not always adding constraints
        // to Object types (types with fields).  When I hit a scenario like that,
        // relax this constraint to make it optional, and update accordingly.
        val fieldName: String, val expectedValue: Any) : Constraint

/**
 * Indicates that an attribute will be returned updated to a value
 * provided by a parameter (ie., an input on a function)
 */
data class AttributeValueFromParameterConstraint(val fieldName: String, val parameterName: String) : Constraint

data class ReturnValueDerivedFromParameterConstraint(val parameterName: String) : Constraint

interface Constraint

typealias FieldName = String
typealias ParamName = String
data class OperationContract(val returnType: Type,
                             val returnTypeConstraints: List<Constraint>
)

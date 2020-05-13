package lang.taxi.services

import lang.taxi.Equality
import lang.taxi.services.operations.constraints.Constraint
import lang.taxi.services.operations.constraints.ConstraintTarget
import lang.taxi.types.*
import lang.taxi.types.Annotation

data class Parameter(override val annotations: List<Annotation>, val type: Type, val name: String?, override val constraints: List<Constraint>) : Annotatable, ConstraintTarget {
    override val description: String = "param $name"
}

data class Operation(val name: String, val scope: String? = null, override val annotations: List<Annotation>, val parameters: List<Parameter>, val returnType: Type, override val compilationUnits: List<CompilationUnit>, val contract: OperationContract? = null) : Annotatable, Compiled {
    private val equality = Equality(this, Operation::name, Operation::annotations, Operation::parameters, Operation::returnType, Operation::contract)

    override fun equals(other: Any?) = equality.isEqualTo(other)
    override fun hashCode(): Int = equality.hash()

}

data class Service(override val qualifiedName: String, val operations: List<Operation>, override val annotations: List<Annotation>, override val compilationUnits: List<CompilationUnit>) : Annotatable, Named, Compiled {
    private val equality = Equality(this, Service::qualifiedName, Service::operations.toSet(), Service::annotations)

    override fun equals(other: Any?) = equality.isEqualTo(other)
    override fun hashCode(): Int = equality.hash()

    fun operation(name: String): Operation {
        return this.operations.first { it.name == name }
    }

    fun containsOperation(name: String) = operations.any { it.name == name }
}

typealias FieldName = String
typealias ParamName = String

data class OperationContract(val returnType: Type,
                             val returnTypeConstraints: List<Constraint>
) : ConstraintTarget {
    override val description: String = "Operation returning ${returnType.qualifiedName}"
    override val constraints: List<Constraint> = returnTypeConstraints
}

package lang.taxi.services

import lang.taxi.Annotatable
import lang.taxi.Type
import lang.taxi.types.Annotation

data class Parameter(override val annotations: List<Annotation>, val type: Type) : Annotatable
data class Operation(val name: String, override val annotations: List<Annotation>, val parameters: List<Parameter>, val returnType: Type) : Annotatable
data class Service(val qualifiedName: String, val operations: List<Operation>, override val annotations: List<Annotation>) : Annotatable {
    fun operation(name: String): Operation {
        return this.operations.first { it.name == name }
    }
}

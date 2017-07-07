package lang.taxi.services

import lang.taxi.Annotatable
import lang.taxi.Type
import lang.taxi.types.Annotation

data class Parameter(override val annotations: List<Annotation>, val type: Type) : Annotatable
data class Method(val name: String, override val annotations: List<Annotation>, val parameters: List<Parameter>, val returnType: Type) : Annotatable
data class Service(val qualifiedName: String, val methods: List<Method>, override val annotations: List<Annotation>) : Annotatable {
    fun method(name: String): Method {
        return this.methods.first { it.name == name }
    }
}

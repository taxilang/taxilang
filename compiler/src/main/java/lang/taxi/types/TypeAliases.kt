package lang.taxi.types

import lang.taxi.Annotatable
import lang.taxi.Type
import lang.taxi.UserType

data class TypeAliasExtension(val annotations: List<Annotation>)
data class TypeAliasDefinition(val aliasType: Type, val annotations: List<Annotation> = emptyList())

data class TypeAlias(
        override val qualifiedName: String,
        override var definition: TypeAliasDefinition?,
        override val extensions: MutableList<TypeAliasExtension> = mutableListOf()
) : UserType<TypeAliasDefinition, TypeAliasExtension>, Annotatable {
    constructor(qualifiedName: String, aliasedType: Type) : this(qualifiedName, TypeAliasDefinition(aliasedType))

    companion object {
        fun undefined(name: String): TypeAlias {
            return TypeAlias(name, definition = null)
        }
    }

    val aliasType: Type?
        get() {
            return definition?.aliasType
        }

    override val annotations: List<Annotation>
        get() {
            val collatedAnnotations = this.extensions.flatMap { it.annotations }.toMutableList()
            definition?.annotations?.forEach { collatedAnnotations.add(it) }
            return collatedAnnotations.toList()
        }

}

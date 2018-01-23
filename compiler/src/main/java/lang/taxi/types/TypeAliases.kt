package lang.taxi.types

import lang.taxi.*

data class TypeAliasExtension(val annotations: List<Annotation>, override val compilationUnit: CompilationUnit) : TypeDefinition {
    private val equalty = Equality(this,TypeAliasExtension::annotations)

    override fun hashCode() = equalty.hash()
    override fun equals(other: Any?) = equalty.isEqualTo(other)
}

data class TypeAliasDefinition(val aliasType: Type, val annotations: List<Annotation> = emptyList(), override val compilationUnit: CompilationUnit) : TypeDefinition {
    private val equalty = Equality(this,TypeAliasDefinition::aliasType,TypeAliasDefinition::annotations)

    override fun hashCode() = equalty.hash()
    override fun equals(other: Any?) = equalty.isEqualTo(other)
}

data class TypeAlias(
        override val qualifiedName: String,
        override var definition: TypeAliasDefinition?,
        override val extensions: MutableList<TypeAliasExtension> = mutableListOf()
) : UserType<TypeAliasDefinition, TypeAliasExtension>, Annotatable {
    constructor(qualifiedName: String, aliasedType: Type, compilationUnit: CompilationUnit) : this(qualifiedName, TypeAliasDefinition(aliasedType, compilationUnit = compilationUnit))
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

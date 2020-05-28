package lang.taxi.types

import lang.taxi.Equality

interface TypeProvider {
    fun getType(qualifiedName: String): Type
}
interface GenericType : Type {
    val parameters: List<Type>

    fun resolveTypes(typeSystem: TypeProvider): GenericType

    override fun toQualifiedName(): QualifiedName {
        val qualifiedName = QualifiedName.from(this.qualifiedName)
        return qualifiedName.copy(parameters = this.parameters.map { it.toQualifiedName() })
    }

}

data class ArrayType(val type: Type, val source: CompilationUnit, override val inheritsFrom: Set<Type> = emptySet()) : GenericType {
    override fun resolveTypes(typeSystem: TypeProvider): GenericType {
        return this.copy(type = typeSystem.getType(type.qualifiedName))
    }

    private val equality = Equality(this, ArrayType::type)
    override fun equals(other: Any?) = equality.isEqualTo(other)
    override fun hashCode(): Int = equality.hash()

    override val compilationUnits: List<CompilationUnit> = listOf(source)
    override val qualifiedName: String = PrimitiveType.ARRAY.qualifiedName
    override val parameters: List<Type> = listOf(type)

   override val format: String? = null


}
interface Annotatable {
    val annotations: List<Annotation>
}

fun List<Annotatable>.annotations(): List<Annotation> {
    return this.flatMap { it.annotations }
}

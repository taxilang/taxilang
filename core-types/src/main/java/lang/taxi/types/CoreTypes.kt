package lang.taxi.types

import lang.taxi.Equality

interface TypeProvider {
    fun getType(qualifiedName: String): Type
}
interface GenericType : Type {
    val parameters: List<Type>

   companion object {
      fun resolvesSameAs(typeA:Type, typeB: Type, considerTypeParameters: Boolean): Boolean {

      }
   }

    fun resolveTypes(typeSystem: TypeProvider): GenericType

    override fun toQualifiedName(): QualifiedName {
        val qualifiedName = QualifiedName.from(this.qualifiedName)
        return qualifiedName.copy(parameters = this.parameters.map { it.toQualifiedName() })
    }

   override fun resolvesSameAs(other: Type, considerTypeParameters: Boolean): Boolean {
      if (!super.resolvesSameAs(other, considerTypeParameters)) {
         return false
      }
      if (other !is GenericType) {
         return false
      }

      if (considerTypeParameters && (this.parameters.size != other.parameters.size)) {
         return false
      }

      val parametersMatch = if (considerTypeParameters) {
         this.parameters.all { parameterType ->
            val index = this.parameters.indexOf(parameterType)
            val otherParameterType = other.parameters[index]
            parameterType.resolvesSameAs(otherParameterType)
         }
      } else {
         true
      }

      return parametersMatch
   }

}

data class ArrayType(val type: Type, val source: CompilationUnit, override val inheritsFrom: Set<Type> = emptySet()) : GenericType {
    override fun resolveTypes(typeSystem: TypeProvider): GenericType {
        return this.copy(type = typeSystem.getType(type.qualifiedName))
    }

    private val wrapper = LazyLoadingWrapper(this)
    override val allInheritedTypes: Set<Type> by lazy { wrapper.allInheritedTypes }
    override val inheritsFromPrimitive: Boolean by lazy { wrapper.inheritsFromPrimitive }
    override val basePrimitive: PrimitiveType? by lazy { wrapper.basePrimitive }
    override val definitionHash: String? by lazy { wrapper.definitionHash }

    private val equality = Equality(this, ArrayType::type)
    override fun equals(other: Any?) = equality.isEqualTo(other)
    override fun hashCode(): Int = equality.hash()

    override val compilationUnits: List<CompilationUnit> = listOf(source)
    override val qualifiedName: String = PrimitiveType.ARRAY.qualifiedName
    override val parameters: List<Type> = listOf(type)

   override val format: List<String>? = null
   override val formattedInstanceOfType: Type? = null
   override val calculation: Formula?
      get() = null
}
interface Annotatable {
    val annotations: List<Annotation>
}

fun List<Annotatable>.annotations(): List<Annotation> {
    return this.flatMap { it.annotations }
}

package lang.taxi.types

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import lang.taxi.ImmutableEquality

interface TypeProvider {
   fun getType(qualifiedName: String): Type
}

data class InvalidNumberOfParametersError(val message: String) {
   companion object {
      fun forTypeAndCount(type: QualifiedName, expectedCount: Int) = InvalidNumberOfParametersError("Type $type expects $expectedCount arguments")
   }
}

interface GenericType : Type {
   val parameters: List<Type>

   fun withParameters(parameters: List<Type>): Either<InvalidNumberOfParametersError, GenericType>

   fun resolveTypes(typeSystem: TypeProvider): GenericType

   override fun toQualifiedName(): QualifiedName {
      val qualifiedName = QualifiedName.from(this.qualifiedName)
      return qualifiedName.copy(parameters = this.parameters.map { it.toQualifiedName() })
   }

}

data class ArrayType(val type: Type, val source: CompilationUnit, override val inheritsFrom: Set<Type> = emptySet()) : GenericType {
   init {
      if (type is ArrayType) {
         ""
      }
   }
   companion object {
      const val NAME = "lang.taxi.Array"
      val qualifiedName = QualifiedName.from(NAME)
      fun isTypedCollection(qualifiedName: QualifiedName): Boolean {
         return qualifiedName.fullyQualifiedName == NAME
            && qualifiedName.parameters.size == 1
      }

      fun untyped(source: CompilationUnit = CompilationUnit.unspecified()) = of(PrimitiveType.ANY, source)
      fun of(type: Type, source: CompilationUnit = CompilationUnit.unspecified(), inheritsFrom: Set<Type> = emptySet()): ArrayType {
         return ArrayType(type, source, inheritsFrom)
      }

      fun isArrayTypeName(requestedTypeName: String): Boolean {
         // Resolve either lang.taxi.Array, or implicitly just Array
         return requestedTypeName == qualifiedName.fullyQualifiedName || requestedTypeName == qualifiedName.typeName
      }
   }

   override val anonymous: Boolean
      get() = type.anonymous

   override fun resolveTypes(typeSystem: TypeProvider): GenericType {
      return this.copy(type = typeSystem.getType(type.qualifiedName))
   }

   private val wrapper = LazyLoadingWrapper(this)
   override val allInheritedTypes: Set<Type> by lazy { wrapper.allInheritedTypes }
   override val inheritsFromPrimitive: Boolean by lazy { wrapper.inheritsFromPrimitive }
   override val basePrimitive: PrimitiveType? by lazy { wrapper.basePrimitive }
   override val definitionHash: String? by lazy { wrapper.definitionHash }

   private val equality = ImmutableEquality(this, ArrayType::type)
   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()

   override val typeDoc: String = "A collection of things"

   override val compilationUnits: List<CompilationUnit> = listOf(source)
   override val qualifiedName: String = NAME
   override val parameters: List<Type> = listOf(type)
   override fun withParameters(parameters: List<Type>): Either<InvalidNumberOfParametersError, GenericType> {
      return if (parameters.size != 1) {
         InvalidNumberOfParametersError.forTypeAndCount(this.toQualifiedName(), 1).left()
      } else {
         this.copy(type = parameters.first()).right()
      }
   }

   override val format: List<String>? = null
   override val formattedInstanceOfType: Type? = null
//   override val calculation: Formula?
//      get() = null
   override val offset: Int? = null
   override val typeKind: TypeKind = TypeKind.Type
}

interface Annotatable {
   val annotations: List<Annotation>
}

fun List<Annotatable>.annotations(): List<Annotation> {
   return this.flatMap { it.annotations }
}

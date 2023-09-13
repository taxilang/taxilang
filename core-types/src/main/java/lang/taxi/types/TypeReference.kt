package lang.taxi.types

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import lang.taxi.ImmutableEquality

/**
 * In Taxi, a reference to a Type.  Part of reflection within taxi
 */
data class TypeReference(val type: Type, val source: CompilationUnit) : GenericType {
   override val inheritsFrom: Set<Type> = emptySet()
   companion object {
      const val NAME = "lang.taxi.Type"
      val qualifiedName = QualifiedName.from(NAME)

      fun isTypeReferenceTypeName(requestedTypeName: QualifiedName): Boolean {
         // Note: Intentionally using fullyQualifiedName, not parameterized name here, since
         // the parameters will affect the name
         return isTypeReferenceTypeName(requestedTypeName.fullyQualifiedName)
      }

      fun isTypeReference(parameterizedName: String): Boolean {
         return parameterizedName.startsWith(TypeReference.NAME)
      }

      fun of(
         type: Type,
         source: CompilationUnit = CompilationUnit.unspecified(),
      ): TypeReference {
         return TypeReference(type, source)
      }

      fun isTypeReferenceTypeName(requestedTypeName: String): Boolean {
         // Resolve either lang.taxi.Type, or implicitly just Type
         return requestedTypeName == TypeReference.qualifiedName.fullyQualifiedName || requestedTypeName == TypeReference.qualifiedName.typeName
      }
      fun untyped(source: CompilationUnit = CompilationUnit.unspecified()) = TypeReference.of(PrimitiveType.ANY, source)
   }

   override fun resolveTypes(typeSystem: TypeProvider): GenericType {
      return this.copy(type = typeSystem.getType(type.qualifiedName))
   }




   private val wrapper = LazyLoadingWrapper(this)
   override val allInheritedTypes: Set<Type> by lazy { wrapper.allInheritedTypes }
   override val inheritsFromPrimitive: Boolean by lazy { wrapper.inheritsFromPrimitive }
   override val basePrimitive: PrimitiveType? by lazy { wrapper.basePrimitive }
   override val definitionHash: String? by lazy { wrapper.definitionHash }

   // Not currently implemented, but could be in the future
   override val annotations: List<lang.taxi.types.Annotation> = emptyList()

   private val equality = ImmutableEquality(this, TypeReference::type)
   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()

   override val typeDoc: String = "Result of a service publishing sequence of events"

   override val compilationUnits: List<CompilationUnit> = listOf(source)
   override val qualifiedName: String = TypeReference.NAME
   override val parameters: List<Type> = listOf(type)
   override fun withParameters(parameters: List<Type>): Either<InvalidNumberOfParametersError, GenericType> {
      return if (parameters.size != 1) {
         InvalidNumberOfParametersError.forTypeAndCount(this.toQualifiedName(), 1).left()
      } else {
         this.copy(type = parameters.first()).right()
      }
   }

   override val format: List<String>? = null

   override val formatAndZoneOffset: FormatsAndZoneOffset? = null
   override val offset: Int? = null
   override val typeKind: TypeKind = TypeKind.Type
}

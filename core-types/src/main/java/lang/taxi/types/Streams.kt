package lang.taxi.types

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import lang.taxi.ImmutableEquality
import kotlin.Annotation

data class StreamType(val type: Type, val source: CompilationUnit, override val inheritsFrom: Set<Type> = emptySet()) :
   GenericType {
   companion object {
      const val NAME = "lang.taxi.Stream"
      val qualifiedName = QualifiedName.from(NAME)

      fun of(
         type: Type,
         source: CompilationUnit = CompilationUnit.unspecified(),
         inheritsFrom: Set<Type> = emptySet()
      ): StreamType {
         return StreamType(type, source, inheritsFrom)
      }

      fun untyped(source: CompilationUnit = CompilationUnit.unspecified()) = StreamType.of(PrimitiveType.ANY, source)

      fun isStreamTypeName(requestedTypeName: QualifiedName): Boolean {
         // Note: Intentionally using fullyQualifiedName, not parameterized name here, since
         // the parameters will affect the name
         return isStreamTypeName(requestedTypeName.fullyQualifiedName)
      }

      fun isStream(parameterizedName: String): Boolean {
         return parameterizedName.startsWith(NAME)
      }
      fun isStream(type: Type):Boolean {
         return isStream(type.toQualifiedName().parameterizedName)
      }

      fun isStreamTypeName(requestedTypeName: String): Boolean {
         // Resolve either lang.taxi.Stream, or implicitly just Stream
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

   // Not currently implemented, but could be in the future
   override val annotations: List<lang.taxi.types.Annotation> = emptyList()

   private val equality = ImmutableEquality(this, StreamType::type)
   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()

   override val typeDoc: String = "Result of a service publishing sequence of events"

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

   override val formatAndZoneOffset: FormatsAndZoneOffset? = null
   override val offset: Int? = null
   override val typeKind: TypeKind = TypeKind.Type
}

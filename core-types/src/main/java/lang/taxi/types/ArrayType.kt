package lang.taxi.types

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import lang.taxi.ImmutableEquality
import lang.taxi.expressions.Expression

data class ArrayType(val type: Type, val source: CompilationUnit, override val inheritsFrom: Set<Type> = emptySet(), val expression: Expression? = null) :
    GenericType {
   // For readability.
   // Should really rename type property
   val memberType = type

   // Not currently implemented, but could be in the future
   override val annotations: List<Annotation> = emptyList()
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

      fun arrayTypeName(memberTypeName: String): String {
         return "$NAME<$memberTypeName>"
      }

      /**
       * If the provided type is an array, returns the member type,
       * otherwise reutrns type
       */
      fun memberTypeIfArray(type: Type): Type {
         return when (type) {
            is ArrayType -> type.memberType
            else -> type
         }
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

   override val formatAndZoneOffset: FormatsAndZoneOffset? = null
   override val format: List<String>? = null
   override val offset: Int? = null
   override val typeKind: TypeKind = TypeKind.Type
}

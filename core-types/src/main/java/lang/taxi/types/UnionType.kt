package lang.taxi.types

import lang.taxi.ImmutableEquality

/**
 * A Union Type is a declaration that could be one of several types - eg: A | B.
 * Currently only partially implemented with usage in Stream queries ( stream { A | B } ).
 * However, need to implement more broadly.
 *
 * Note: UnionTypes are treated as structural types (like Arrays or Streams), in that they are not
 * registered directly with the schema, but created on demand
 *
 */
data class UnionType(
   val types: List<Type>,
   override val typeDoc: String?,
   override val annotations: List<Annotation>,
   val source: CompilationUnit
) : Type {
   // Design choice:
   // We don't register union types in the schema
   // This was causing problems when multiple queries / streams ended up defining the same
   // UnionType, and trying to register them.
   // We could've taken a relaxed approach, only registering if the type didn't exist already.
   // Hoewver, that would make UnionTypes "special", in comparison to other structural types
   // like Maps, Arrays and Streams.
   // On the down-side, we end up having to do some string manipulation in the name,
   // which could lead to edge cases
   companion object {
      private const val PREFIX = "UnionType$"
      private const val SEPERATOR = "$$"
      fun unionTypeName(types: List<Type>) = "$PREFIX${types.joinToString(SEPERATOR) { it.qualifiedName }}"
      fun isUnionType(type: Type): Boolean = type is UnionType
      fun isUnionType(name: QualifiedName): Boolean = name.parameterizedName.startsWith(PREFIX)
      fun getTypeNames(qualifiedName: QualifiedName): List<QualifiedName> {
         // I wish we could do this without string manipulation.
         // See above
         return qualifiedName.parameterizedName.removePrefix(PREFIX)
            .split("$$")
            .map { QualifiedName.from(it) }

      }
   }
   private val wrapper = LazyLoadingWrapper(this)
   private val equality = ImmutableEquality(this, UnionType::types, UnionType::annotations)
   override fun equals(other: Any?): Boolean  = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()

   override val inheritsFrom: Set<Type> = emptySet()
   override val allInheritedTypes: Set<Type> = emptySet()
   override val inheritsFromPrimitive: Boolean = false
   override val basePrimitive: PrimitiveType? = null
   override val definitionHash: String? by lazy { wrapper.definitionHash }
   override val typeKind: TypeKind? = null
   override val format: List<String>? = null
   override val offset: Int? = null
   override val formatAndZoneOffset: FormatsAndZoneOffset? = null
   override val qualifiedName: String = unionTypeName(this.types)
   override val compilationUnits: List<CompilationUnit> = listOf(source)

   val fields:Set<Field> = types.flatMap {
      when (it) {
         // In a union type, we're making all fields nullable, as we can't assert
         // that it is definitely present.
         is ObjectType -> it.allFields.map { field -> field.copy(nullable = true) }
         else -> emptySet<Field>()
      }
   }.toSet()
}

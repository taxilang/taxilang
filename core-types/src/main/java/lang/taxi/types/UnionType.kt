package lang.taxi.types

import lang.taxi.ImmutableEquality
import lang.taxi.expressions.TypeExpression
import lang.taxi.types.PrimitiveType.Companion.INHERITS_FROM_ANY
import lang.taxi.types.UnionType.Companion.unionTypeName

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
   override val types: List<Type>,
   override val annotations: List<Annotation>,
   val source: CompilationUnit
) : SumType(types, source) {
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
      private const val SEPARATOR = "$$"
      fun unionTypeName(types: List<Type>) = "$PREFIX${types.joinToString(SEPARATOR) { it.qualifiedName }}"
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
   override val qualifiedName: String = unionTypeName(this.types)
   private val equality = ImmutableEquality(this, UnionType::types, UnionType::annotations)
   override fun equals(other: Any?): Boolean = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()
}

data class IntersectionType(
   override val types: List<Type>,
   override val annotations: List<Annotation>,
   val source: CompilationUnit
) : SumType(types, source) {
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
      private const val PREFIX = "Intersection$"
      private const val SEPARATOR = "$$"
      fun intersectionTypeName(types: List<Type>) = "$PREFIX${types.joinToString(SEPARATOR) { it.qualifiedName }}"
      fun isIntersectionType(type: Type): Boolean = type is UnionType
      fun isIntersectionType(name: QualifiedName): Boolean = name.parameterizedName.startsWith(PREFIX)
      fun getTypeNames(qualifiedName: QualifiedName): List<QualifiedName> {
         // I wish we could do this without string manipulation.
         // See above
         return qualifiedName.parameterizedName.removePrefix(PREFIX)
            .split("$$")
            .map { QualifiedName.from(it) }

      }
   }
   override val qualifiedName: String = intersectionTypeName(this.types)
   private val equality = ImmutableEquality(this, IntersectionType::types, IntersectionType::annotations)
   override fun equals(other: Any?): Boolean = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()
}

/**
 * A sum type is the base type for Intersection and Union types.
 * Both have the same behaviours from a modelling perspective, and
 * follow the same rules for field merging.
 * However, they have different behaviours at query time.
 *
 * See ADR: 20241120-refine-the-behaviour-of-sum-types-union-and-intersection.md
 */
abstract class SumType(
   types: List<Type>,
   private val source: CompilationUnit
) : Type {

   // Sum types don't have docs, as they're declared
   // as a type expression in a parent type.
   // eg:
   // type Foo = A | B
   // so the docs are attached to Foo
   override val typeDoc: String? = null
   abstract val types: List<Type>

   private val wrapper = LazyLoadingWrapper(this)

   override val inheritsFrom: List<Type> = INHERITS_FROM_ANY
   override val allInheritedTypes: Set<Type> = emptySet()

   @Deprecated("Everything now inherits from Any (previously that wasn't true). Consider using isScalar if that's really want you want to know")
   override val inheritsFromPrimitive: Boolean = false
   override val basePrimitive: PrimitiveType? = null
   override val definitionHash: String? by lazy { wrapper.definitionHash }
   override val typeKind: TypeKind? = null
   override val format: List<String>? = null
   override val offset: Int? = null
   override val formatAndZoneOffset: FormatsAndZoneOffset? = null
   override val compilationUnits: List<CompilationUnit> = listOf(source)

   companion object {
      fun isSumTypeOrWrapper(type: Type): Boolean {
         return sumTypeOrNull(type) != null
      }

      fun sumTypeOrNull(type: Type): SumType? {
         return when {
            type is SumType -> type
            type is ObjectType && type.expression is TypeExpression && type.expression!!.returnType is SumType -> type.expression!!.returnType as SumType
            else -> null
         }
      }
   }
   /**
    * The fields of a union type are only the fields
    * that are present in ALL types
    */
   val fields: Set<Field> = types.flatMap { type ->
      when (type) {
         is ObjectType -> type.allFields.map { field ->
            // Check if this field name exists in other types
            val conflictingFields = types
               .filterIsInstance<ObjectType>()
               .filter { it != type }
               .flatMap { it.allFields }
               .filter { it.name == field.name }


            val safelyNamedField = when {
               conflictingFields.isEmpty() -> field
               conflictingFields.all { it.type == field.type } -> field
               else -> {
                  // Prepend model name or fully qualified name if needed
                  val prefix =
                     if (types.count { it.toQualifiedName().typeName == type.toQualifiedName().typeName } > 1) {
                        type.toQualifiedName().parameterizedName
                     } else {
                        type.toQualifiedName().typeName
                     }
                  val safePrefix = prefix
                     .replace("<", "")
                     .replace(">", "")
                     .replace(".", "_")
                     .replace(",", "")
                  field.copy(name = "${safePrefix}_${field.name}")
               }
            }
            // In a union type, we're making all fields nullable, as we can't assert
            // that it is definitely present.
            safelyNamedField.copy(nullable = true)
         }

         else -> emptySet<Field>()
      }
   }.toSet()

}

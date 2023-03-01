package lang.taxi.types

import arrow.core.Either
import arrow.core.right
import lang.taxi.ImmutableEquality

data class TypeAliasExtension(
   val annotations: List<Annotation>,
   override val compilationUnit: CompilationUnit,
   override val typeDoc: String? = null
) : TypeDefinition, Documented {
   private val equalty = ImmutableEquality(this, TypeAliasExtension::annotations)

   override fun hashCode() = equalty.hash()
   override fun equals(other: Any?) = equalty.isEqualTo(other)
}

data class TypeAliasDefinition(
   val aliasType: Type,
   val annotations: List<Annotation> = emptyList(),
   override val compilationUnit: CompilationUnit,
   override val typeDoc: String? = null
) : TypeDefinition, Documented {
   private val equalty = ImmutableEquality(this, TypeAliasDefinition::aliasType, TypeAliasDefinition::annotations)

   override fun hashCode() = equalty.hash()
   override fun equals(other: Any?) = equalty.isEqualTo(other)
}

data class TypeAlias(
   override val qualifiedName: String,
   override var definition: TypeAliasDefinition?,
   override val extensions: MutableList<TypeAliasExtension> = mutableListOf()
) : UserType<TypeAliasDefinition, TypeAliasExtension>, Annotatable, Documented {
   constructor(qualifiedName: String, aliasedType: Type, compilationUnit: CompilationUnit) : this(
      qualifiedName,
      TypeAliasDefinition(aliasedType, compilationUnit = compilationUnit)
   )

   private val wrapper = LazyLoadingWrapper(this)
   override val allInheritedTypes: Set<Type>
      get() {
         return if (isDefined) wrapper.allInheritedTypes else emptySet()
      }
   override val inheritsFromPrimitive: Boolean
      get() {
         return if (isDefined) wrapper.inheritsFromPrimitive else false
      }
   override val basePrimitive: PrimitiveType?
      get() {
         return if (isDefined) wrapper.basePrimitive else null
      }
   override val definitionHash: String?
      get() {
         return if (isDefined) wrapper.definitionHash else null
      }

   // Don't support a type alias overriding the format of it's aliased type,
   // as then they're no longer synonyms
   override val format: List<String>?
      get() = definition?.aliasType?.format

   override val formatAndZoneOffset: FormatsAndZoneOffset?
      get() = definition?.aliasType?.formatAndZoneOffset
   override val offset: Int?
      get() = definition?.aliasType?.offset

   override val inheritsFrom: Set<Type> = definition?.aliasType?.inheritsFrom ?: emptySet()

   override val typeDoc: String?
      get() = Documented.typeDoc(listOf(definition) + extensions)

   override val referencedTypes: List<Type>
      get() {
         return if (isDefined) listOf(this.aliasType!!) else emptyList()
      }

//   override val calculation: Formula?
//      get() = definition?.aliasType?.calculation

   override fun addExtension(extension: TypeAliasExtension): Either<ErrorMessage, TypeAliasExtension> {
      this.extensions.add(extension)
      return extension.right()
   }

   companion object {
      fun undefined(name: String): TypeAlias {
         return TypeAlias(name, definition = null)
      }

      fun underlyingType(type: Type): Type {
         return when (type) {
            is TypeAlias -> underlyingType(type.aliasType!!)
            else -> type
         }
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

   override val typeKind: TypeKind?
      get() = aliasType?.typeKind
}

package lang.taxi.types

/**
 * A Union Type is a declaration that could be one of several types - eg: A | B.
 * Currently only partially implemented with usage in Stream queries ( stream { A | B } ).
 * However, need to implement more broadly.
 */
data class UnionType(
   val types: List<Type>,
   override val typeDoc: String?,
   override val annotations: List<Annotation>,
   val source: CompilationUnit
) : Type {
   companion object {
      fun unionTypeName(types: List<Type>) = "UnionType${types.joinToString("_") { it.qualifiedName }}"
      fun isUnionType(type: Type): Boolean = type is UnionType
   }
   private val wrapper = LazyLoadingWrapper(this)


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

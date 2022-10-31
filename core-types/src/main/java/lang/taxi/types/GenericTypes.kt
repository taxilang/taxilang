package lang.taxi.types

/**
 * An unresolved generic argument within a function declaration
 * eg: declare function <T> sum(T[]):T
 */
data class TypeArgument(
   override val qualifiedName: String,
   // This is the actual name assigned in the functio - ie., given function <T>, it's T
   val declaredName:String,
   override val inheritsFrom: Set<Type> = emptySet(),
   override val compilationUnits: List<CompilationUnit>
) : Type {
   private val wrapper = LazyLoadingWrapper(this)
   override val allInheritedTypes: Set<Type> = wrapper.allInheritedTypes
   override val format: List<String>? = null
   override val inheritsFromPrimitive: Boolean = wrapper.inheritsFromPrimitive
   override val basePrimitive: PrimitiveType? = wrapper.basePrimitive
   override val formattedInstanceOfType: Type? = null
   override val definitionHash: String? = wrapper.definitionHash
//   override val calculation: Formula? = null
   override val offset: Int? = null
   override val typeKind: TypeKind? = TypeKind.Type
   override val typeDoc: String? = null

   // Not currently implemented, but could be in the future
   override val annotations: List<Annotation> = emptyList()
}

package lang.taxi.types

interface Type : Named, Compiled {
   val inheritsFrom: Set<Type>

   val allInheritedTypes: Set<Type>
      get() {
         return getInheritanceGraph()
      }

   private fun getInheritanceGraph(typesToExclude: Set<Type> = emptySet()): Set<Type> {
      val allExcludedTypes: Set<Type> = typesToExclude + setOf(this)
      return this.inheritsFrom
         .flatMap { inheritedType ->
            if (!typesToExclude.contains(inheritedType))
               setOf(inheritedType) + inheritedType.getInheritanceGraph(allExcludedTypes)
            else emptySet<Type>()
         }.toSet()
   }


}

interface TypeDefinition {
   val compilationUnit: CompilationUnit
}

interface Documented {
   val typeDoc: String?

   companion object {
      fun typeDoc(sources: List<Documented?>): String? {
         return sources.filterNotNull().typeDoc()
      }
   }

}


/**
 * A type that can be declared by users explicity.
 * eg:  Object type, Enum type.
 * ArrayType is excluded (as arrays are primitive, and the inner
 * type will be a UserType)
 */
interface UserType<TDef : TypeDefinition, TExt : TypeDefinition> : Type {
   var definition: TDef?

   val extensions: List<TExt>

   fun addExtension(extension: TExt): ErrorMessage?

   val isDefined: Boolean
      get() {
         return this.definition != null
      }

   override val compilationUnits: List<CompilationUnit>
      get() = (this.extensions.map { it.compilationUnit } + this.definition?.compilationUnit).filterNotNull()

   /**
    * A list of all the other types this UserType makes reference to.
    * Used when importing this type, to ensure the full catalogue of types is imported
    */
   val referencedTypes: List<Type>

}


fun List<Documented>.typeDoc(): String? {
   return this.mapNotNull { it.typeDoc }.joinToString("\n")
}

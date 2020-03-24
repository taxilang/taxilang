package lang.taxi.types

import lang.taxi.*

data class EnumValue(val name: String, override val annotations: List<Annotation>, override val typeDoc: String? = null) : Annotatable, Documented
data class EnumDefinition(val values: List<EnumValue>,
                          override val annotations: List<Annotation> = emptyList(),
                          override val compilationUnit: CompilationUnit,
                          val inheritsFrom: Set<Type> = emptySet(),
                          override val typeDoc: String? = null) : Annotatable, TypeDefinition, Documented {
   private val equality = Equality(this, EnumDefinition::values.toSet(), EnumDefinition::annotations.toSet())
   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()

}

data class EnumType(override val qualifiedName: String,
                    override var definition: EnumDefinition?,
                    override val extensions: MutableList<EnumDefinition> = mutableListOf()) : UserType<EnumDefinition, EnumDefinition>, Annotatable, Documented {
   companion object {
      fun undefined(name: String): EnumType {
         return EnumType(name, definition = null)
      }
   }

   override val inheritsFrom: Set<Type>
      get() = definition?.inheritsFrom ?: emptySet()

   override val typeDoc: String?
      get() = (listOfNotNull(this.definition) + extensions).typeDoc()

   override val referencedTypes: List<Type> = emptyList()

   override fun addExtension(extension: EnumDefinition): ErrorMessage? {
      if (!this.isDefined) {
         error("It is invalid to add an extension before the enum is defined")
      }
      val definedValueNames = this.definition!!.values.map { it.name }
      // Validate that the extension doesn't modify members
      val illegalValueDefinitions = extension.values.filter { value -> !definedValueNames.contains(value.name) }
      if (illegalValueDefinitions.isNotEmpty()) {
         val illegalValueNames = illegalValueDefinitions.joinToString(", ") { it.name }
         return "Cannot modify the members in an enum.  An extension attempted to add a new members $illegalValueNames"
      }
      this.extensions.add(extension)
      return null
   }

   val values: List<EnumValue>
      get() {
         return this.definition?.values?.map { value ->
            val valueExtensions: List<EnumValue> = valueExtensions(value.name)
            val collatedAnnotations = value.annotations + valueExtensions.annotations()
            value.copy(annotations = collatedAnnotations, typeDoc = (listOf(value) + valueExtensions).typeDoc())
         } ?: emptyList()
      }

   private fun valueExtensions(valueName: String): List<EnumValue> {
      return this.extensions.flatMap { it.values.filter { value -> value.name == valueName } }
   }

   override val annotations: List<Annotation>
      get() {
         val collatedAnnotations = this.extensions.flatMap { it.annotations }.toMutableList()
         definition?.annotations?.forEach { collatedAnnotations.add(it) }
         return collatedAnnotations.toList()
      }

   fun value(valueName: String): EnumValue {
      return this.values.first { it.name == valueName }
   }
}

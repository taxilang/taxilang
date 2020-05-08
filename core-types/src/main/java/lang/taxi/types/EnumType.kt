package lang.taxi.types

import lang.taxi.*

data class EnumValueExtension(val name: String, override val annotations: List<Annotation>, override val typeDoc: String? = null) : Annotatable, Documented
data class EnumValue(val name: String, val value: Any, override val annotations: List<Annotation>, override val typeDoc: String? = null) : Annotatable, Documented
data class EnumDefinition(val values: List<EnumValue>,
                          override val annotations: List<Annotation> = emptyList(),
                          override val compilationUnit: CompilationUnit,
                          val inheritsFrom: Set<Type> = emptySet(),
                          val basePrimitive: PrimitiveType,
                          override val typeDoc: String? = null) : Annotatable, TypeDefinition, Documented {
   private val equality = Equality(this, EnumDefinition::values.toSet(), EnumDefinition::annotations.toSet(), EnumDefinition::typeDoc, EnumDefinition::basePrimitive, EnumDefinition::inheritsFrom.toSet())
   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()
}

data class EnumExtension(val values: List<EnumValueExtension>,
                         override val annotations: List<Annotation> = emptyList(),
                         override val compilationUnit: CompilationUnit,
                         override val typeDoc: String? = null) : Annotatable, TypeDefinition, Documented {
   private val equality = Equality(this, EnumExtension::values.toSet(), EnumExtension::annotations.toSet(), EnumExtension::typeDoc)
   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()
}

data class EnumType(override val qualifiedName: String,
                    override var definition: EnumDefinition?,
                    override val extensions: MutableList<EnumExtension> = mutableListOf()) : UserType<EnumDefinition, EnumExtension>, Annotatable, Documented {
   companion object {
      fun undefined(name: String): EnumType {
         return EnumType(name, definition = null)
      }
   }

   override val basePrimitive: PrimitiveType?
      get() = definition?.basePrimitive

   override val inheritsFrom: Set<Type>
      get() = definition?.inheritsFrom ?: emptySet()

   override val typeDoc: String?
      get() {
         val documented = (listOfNotNull(this.definition) + extensions) as List<Documented>
         return documented.typeDoc()
      }

   override val referencedTypes: List<Type> = emptyList()

   override fun addExtension(extension: EnumExtension): ErrorMessage? {
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
            val valueExtensions: List<EnumValueExtension> = valueExtensions(value.name)
            val collatedAnnotations = value.annotations + valueExtensions.annotations()
            val docSources = (listOf(value) + valueExtensions) as List<Documented>
            value.copy(annotations = collatedAnnotations, typeDoc = docSources.typeDoc())
         } ?: emptyList()
      }

   private fun valueExtensions(valueName: String): List<EnumValueExtension> {
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

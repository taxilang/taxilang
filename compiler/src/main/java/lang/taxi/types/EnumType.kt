package lang.taxi.types

import lang.taxi.*

data class EnumValue(val name: String, override val annotations: List<Annotation>) : Annotatable
data class EnumDefinition(val values: List<EnumValue>,
                          override val annotations: List<Annotation> = emptyList(),
                          override val compilationUnit: CompilationUnit) : Annotatable, TypeDefinition

data class EnumType(override val qualifiedName: String,
                    override var definition: EnumDefinition?,
                    override val extensions: MutableList<EnumDefinition> = mutableListOf()) : UserType<EnumDefinition, EnumDefinition>, Annotatable {
   companion object {
      fun undefined(name: String): EnumType {
         return EnumType(name, definition = null)
      }
   }

   val values: List<EnumValue>
      get() {
         return this.definition?.values?.map { value ->
            val valueExtensions: List<EnumValue> = valueExtensions(value.name)
            val collatedAnnotations = value.annotations + valueExtensions.annotations()
            value.copy(annotations = collatedAnnotations)
         } ?: emptyList()
      }

   private fun valueExtensions(valueName: String): List<EnumValue> {
      return this.extensions.flatMap { it.values.filter { it.name == valueName } }
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

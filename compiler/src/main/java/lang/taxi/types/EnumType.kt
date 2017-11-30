package lang.taxi.types

import lang.taxi.Annotatable
import lang.taxi.SourceCode
import lang.taxi.UserType
import lang.taxi.annotations

data class EnumValue(val name: String, override val annotations: List<Annotation>) : Annotatable
data class EnumDefinition(val values: List<EnumValue>,
                          val annotations: List<Annotation> = emptyList(),
                          val source:SourceCode = SourceCode.unspecified())

data class EnumType(override val qualifiedName: String,
                    override var definition: EnumDefinition?,
                    override val extensions: MutableList<EnumDefinition> = mutableListOf()) : UserType<EnumDefinition, EnumDefinition>, Annotatable {
   companion object {
      fun undefined(name: String): EnumType {
         return EnumType(name, definition = null)
      }
   }

   override val sources: List<SourceCode>
      get() = (this.extensions.map { it.source } + this.definition?.source).filterNotNull()

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

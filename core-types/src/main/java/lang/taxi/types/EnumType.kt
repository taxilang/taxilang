package lang.taxi.types

import arrow.core.Either
import lang.taxi.Equality

object Enums {
   fun enumValue(enum: QualifiedName, enumValueName: String): EnumValueQualifiedName {
      return "${enum.parameterizedName}.$enumValueName"
   }

   fun splitEnumValueQualifiedName(name: EnumValueQualifiedName): Pair<QualifiedName, String> {
      val parts = name.split(".")
      val enumName = parts.dropLast(1).joinToString(".")
      val valueName = parts.last()
      return QualifiedName.from(enumName) to valueName
   }

}
typealias EnumValueQualifiedName = String

data class EnumValueExtension(val name: String, override val annotations: List<Annotation>, val synonyms: List<EnumValueQualifiedName>, override val typeDoc: String? = null, override val compilationUnit: CompilationUnit) : Annotatable, Documented, TypeDefinition
data class EnumValue(val name: String, val value: Any = name, val qualifiedName: EnumValueQualifiedName, override val annotations: List<Annotation>, val synonyms: List<EnumValueQualifiedName>, override val typeDoc: String? = null) : Annotatable, Documented
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

   private val wrapper = LazyLoadingWrapper(this)
   override val allInheritedTypes: Set<Type>
      get() {
         return if (isDefined) wrapper.allInheritedTypes else emptySet()
      }
   override val baseEnum: EnumType?
      get() {
         return if (isDefined) wrapper.baseEnum else null
      }
   override val inheritsFromPrimitive: Boolean
      get() {
         return if (isDefined) wrapper.inheritsFromPrimitive else false
      }

   // Not sure it makes sense to support formats on enums.  Let's revisit if there's a usecase.
   override val format: String? = null

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

   override fun addExtension(extension: EnumExtension): Either<ErrorMessage, EnumExtension> {
      if (!this.isDefined) {
         error("It is invalid to add an extension before the enum is defined")
      }
      val definedValueNames = this.definition!!.values.map { it.name }
      // Validate that the extension doesn't modify members
      val illegalValueDefinitions = extension.values.filter { value -> !definedValueNames.contains(value.name) }
      return if (illegalValueDefinitions.isNotEmpty()) {
         val illegalValueNames = illegalValueDefinitions.joinToString(", ") { it.name }
         Either.left("Cannot modify the members in an enum.  An extension attempted to add a new members $illegalValueNames")
      } else {
         this.extensions.add(extension)
         Either.right(extension)
      }
   }

   val values: List<EnumValue>
      get() {
         return this.definition?.values?.map { value ->
            val valueExtensions: List<EnumValueExtension> = valueExtensions(value.name)
            val collatedAnnotations = value.annotations + valueExtensions.annotations()
            val docSources = (listOf(value) + valueExtensions) as List<Documented>
            val synonyms = value.synonyms + valueExtensions.flatMap { it.synonyms }
            value.copy(annotations = collatedAnnotations, typeDoc = docSources.typeDoc(), synonyms = synonyms)
         } ?: emptyList()
      }

   fun has(valueOrName: Any?): Boolean {
      return (valueOrName is String && this.hasName(valueOrName)) || this.hasValue(valueOrName)
   }

   fun hasName(name: String?): Boolean {
      return this.values.any { it.name == name }
   }

   fun hasValue(value: Any?): Boolean {
      return this.values.any { it.value == value }
   }

   fun ofValue(value: Any?)  = this.values.first { it.value == value}
   fun ofName(name: String?)  = this.values.first { it.name == name}
   fun of(valueOrName: Any?) = this.values.first { it.value == valueOrName || it.name == valueOrName }

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

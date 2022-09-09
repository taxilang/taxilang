package lang.taxi.types

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.google.common.cache.CacheBuilder
import lang.taxi.ImmutableEquality

object Enums {
   fun enumValue(enum: QualifiedName, enumValueName: String): EnumValueQualifiedName {
      return "${enum.parameterizedName}.$enumValueName"
   }

   fun isPotentialEnumMemberReference(value:String):Boolean {
      // WOuld be nice to have a smarter check here
      return value.contains(".")
   }

   fun splitEnumValueQualifiedName(name: EnumValueQualifiedName): Pair<QualifiedName, String> {
      val parts = name.split(".")
      val enumName = parts.dropLast(1).joinToString(".")
      val valueName = parts.last()
      return QualifiedName.from(enumName) to valueName
   }

}
/**
 * A qualified name in the form of EnumName.EnumValue
 * Parse this uses EnumValue.qualifiedNameFrom(it).
 * Alternatively, consider using Enum.member(name) to retrieve an EnumMember, which contains
 * a reference to both the enum and the value.
 */
typealias EnumValueQualifiedName = String

data class EnumValueExtension(val name: String,
                              override val annotations: List<Annotation>,
                              val synonyms: List<EnumValueQualifiedName>,
                              override val typeDoc: String? = null,
                              override val compilationUnit: CompilationUnit) : Annotatable, Documented, TypeDefinition {
   private val equality = ImmutableEquality(this, EnumValueExtension::name, EnumValueExtension::annotations, EnumValueExtension::synonyms, EnumValueExtension::typeDoc)
   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()
}
data class EnumValue(
   val name: String,
   val value: Any = name,
   val qualifiedName: EnumValueQualifiedName,
   override val annotations: List<Annotation> = emptyList(),
   val synonyms: List<EnumValueQualifiedName> = emptyList(),
   override val typeDoc: String? = null,
   val isDefault: Boolean = false
) : Annotatable, Documented {
   companion object {
      fun enumValueQualifiedName(enum:EnumType, valueName:String): EnumValueQualifiedName {
         return enumValueQualifiedName(enum.toQualifiedName(),valueName)
      }
      fun enumValueQualifiedName(enumName:QualifiedName, valueName: String):EnumValueQualifiedName {
         return "${enumName.fullyQualifiedName}.$valueName"
      }
      fun splitEnumValueName(name: EnumValueQualifiedName): Pair<QualifiedName, String> {
         val parts = name.split(".")
         return QualifiedName.from(parts.dropLast(1).joinToString(".")) to parts.last()
      }
   }
}

data class EnumDefinition(val values: List<EnumValue>,
                          override val annotations: List<Annotation> = emptyList(),
                          override val compilationUnit: CompilationUnit,
                          val inheritsFrom: Set<Type> = emptySet(),
                          val basePrimitive: PrimitiveType,
                          val isLenient: Boolean = false,
                          override val typeDoc: String? = null) : Annotatable, TypeDefinition, Documented {
   private val equality = ImmutableEquality(this, EnumDefinition::values, EnumDefinition::annotations, EnumDefinition::typeDoc, EnumDefinition::basePrimitive, EnumDefinition::inheritsFrom)
   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()
}


data class EnumExtension(val values: List<EnumValueExtension>,
                         override val annotations: List<Annotation> = emptyList(),
                         override val compilationUnit: CompilationUnit,
                         override val typeDoc: String? = null) : Annotatable, TypeDefinition, Documented {
   private val equality = ImmutableEquality(this, EnumExtension::values, EnumExtension::annotations, EnumExtension::typeDoc)
   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()
}

/**
 * Simple structure containing both the defining enum, and a value within it.
 */
data class EnumMember(val enum: EnumType, val value: EnumValue) {
   val qualifiedName:EnumValueQualifiedName = value.qualifiedName
}

data class EnumType(override val qualifiedName: String,
                    override var definition: EnumDefinition?,
                    override val extensions: MutableList<EnumExtension> = mutableListOf()) : UserType<EnumDefinition, EnumExtension>, Annotatable, Documented {
   companion object {
      fun undefined(name: String): EnumType {
         return EnumType(name, definition = null)
      }
   }

   private val members:Map<EnumValue,EnumMember> by lazy {
      this.values.map { value -> value to EnumMember(this,value) }.toMap()
   }

   val isLenient: Boolean
      get() {
         return this.definition?.isLenient ?: false
      }

   private val wrapper = LazyLoadingWrapper(this)
   override val allInheritedTypes: Set<Type>
      get() {
         return if (isDefined) wrapper.allInheritedTypes else emptySet()
      }
   val baseEnum: EnumType?
      get() {
         return if (isDefined) wrapper.baseEnum else null
      }
   override val inheritsFromPrimitive: Boolean
      get() {
         return if (isDefined) wrapper.inheritsFromPrimitive else false
      }
   override val definitionHash: String?
      get() {
         return if (isDefined) wrapper.definitionHash else null
      }

   // Not sure it makes sense to support formats on enums.  Let's revisit if there's a usecase.
   override val format: List<String>? = null
   override val offset: Int? = null
   override val formattedInstanceOfType: Type? = null
//   override val calculation: Formula?
//      get() = null

   override val basePrimitive: PrimitiveType?
      get() = definition?.basePrimitive

   override val inheritsFrom: Set<Type>
      get() = definition?.inheritsFrom ?: emptySet()

   val inheritsFromNames: List<String>
      get() {
         return inheritsFrom.map { it.qualifiedName }
      }

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
         "Cannot modify the members in an enum.  An extension attempted to add a new members $illegalValueNames".left()
      } else {
         this.extensions.add(extension)
         extension.right()
      }
   }

   val defaultValue: EnumValue?
      get() {
         return values.firstOrNull { it.isDefault }
      }

   val hasDefault: Boolean
      get() {
         return defaultValue != null
      }

   private val enumValueCache = CacheBuilder
      .newBuilder()
      .build<String, List<EnumValue>>()
   val values: List<EnumValue>
      get() {
         return when {
            this.baseEnum != this -> this.baseEnum?.values ?: emptyList()
            this.definition == null -> emptyList()
            else -> {
               // through profiling perfomance whne using the compiler in the LSP tooling,
               // we've found that calls to Enum.values() happens a lot, and is expensive.
               // Therefore, we're caching this.
               // Note the cache must invaliate when things that affect the list of values change.
               // THat's either the definition, or adding an extension.
               val cacheKey = "${this.definition!!.hashCode()}-${this.extensions.hashCode()}"
               this.enumValueCache.get(cacheKey) {
                  this.definition!!.values.map { value ->
                     val valueExtensions: List<EnumValueExtension> = valueExtensions(value.name)
                     val collatedAnnotations = value.annotations + valueExtensions.annotations()
                     val docSources = (listOf(value) + valueExtensions) as List<Documented>
                     val synonyms = value.synonyms + valueExtensions.flatMap { it.synonyms }
                     value.copy(annotations = collatedAnnotations, typeDoc = docSources.typeDoc(), synonyms = synonyms)
                  }
               }
            }
         }
      }

   fun has(valueOrName: Any?): Boolean {
      return (valueOrName is String && this.hasName(valueOrName)) || this.hasValue(valueOrName) || this.hasDefault
   }

   fun hasName(name: String?): Boolean {
      return this.values.any { lenientEqual(it.name, name) } || this.hasDefault
   }

   fun hasValue(value: Any?): Boolean {
      return hasExplicitValue(value)  || this.hasDefault
   }

   /**
    * Examines is a value is defined that matches lenient rules.
    * Ignores default values if defined.
    */
   fun hasExplicitValue(value: Any?): Boolean {
      return this.values.any { lenientEqual(it.value, value) }
   }

   private fun lenientEqual(first: Any, second: Any?): Boolean {
      // Regardless of lenient, we toString() the values, so that an enum of "1" matches a value of 1, and vice versa.
      // This is a concious choice.
      return if (!this.isLenient) {
         first.toString() == second.toString()
      } else {
         when {
            (first is String && second is String) -> first.toLowerCase() == second.toLowerCase()
            else -> first.toString() == second.toString()
         }
      }
   }

   fun ofValue(value: Any?) = this.values.firstOrNull { lenientEqual(it.value, value) }
      ?: defaultValue
      ?: error("Enum ${this.qualifiedName} does not contain a member with a value of $value")

   fun ofName(name: String?) = this.values.firstOrNull { lenientEqual(it.name, name) }
      ?: defaultValue
      ?: error("Enum ${this.qualifiedName} does not contains a member named $name")

   fun of(valueOrName: Any?) = this.values.firstOrNull { lenientEqual(it.value, valueOrName) || lenientEqual(it.name, valueOrName) }
      ?: defaultValue
      ?: error("Enum ${this.qualifiedName} does not contain either a name nor a value of $valueOrName")

   fun member(valueOrName:Any?):EnumMember {
      return members[this.of(valueOrName)] ?: error("Enum ${this.qualifiedName} does nto contain a member with either name or value of $valueOrName")
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

   @Deprecated("Use ofName(), as this method name is confusing")
   fun value(valueName: String): EnumValue {
      return ofName(valueName)
   }

   /**
    * Returns true if the provided value would resolve
    * only through a default value
    */
   fun resolvesToDefault(valueOrName: Any): Boolean {
      return if (!this.hasDefault) {
         false
      } else {
         this.values.none { lenientEqual(it.name, valueOrName) }
            && this.values.none { lenientEqual(it.value, valueOrName) }
      }
   }

   override val typeKind: TypeKind = TypeKind.Type
}

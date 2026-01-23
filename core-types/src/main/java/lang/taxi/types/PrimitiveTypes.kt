package lang.taxi.types

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import lang.taxi.sources.SourceCode
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.EnumSet


enum class VoidType : Type {
   VOID;

   private val wrapper = LazyLoadingWrapper(this)
   override val allInheritedTypes: Set<Type> by lazy { wrapper.allInheritedTypes }
   override val inheritsFromPrimitive: Boolean by lazy { wrapper.inheritsFromPrimitive }
   override val basePrimitive: PrimitiveType? by lazy { wrapper.basePrimitive }
   override val definitionHash: String? by lazy { wrapper.definitionHash }
   override val typeDoc: String = "Nothing"

   override val qualifiedName: String = "lang.taxi.Void"
   override val compilationUnits: List<CompilationUnit> =
      listOf(CompilationUnit.ofSource(SourceCode("Built in", "// Built-in type")))
   override val inheritsFrom: List<Type> = listOf(PrimitiveType.ANY)
   override val format: List<String>? = null
   override val formatAndZoneOffset: FormatsAndZoneOffset? = null
   override val offset: Int? = null
   override val typeKind: TypeKind = TypeKind.Type
   override val isScalar: Boolean = true

   // Not currently implemented, but could be in the future
   override val annotations: List<lang.taxi.types.Annotation> = emptyList()

}

object NumberTypes {
   // Order is important here, list the number types in
   // order of specificity.
   // Numeric expressions ensure that the return type
   // is the most specific
   val NUMBER_TYPES = listOf(PrimitiveType.INTEGER, PrimitiveType.LONG, PrimitiveType.DECIMAL, PrimitiveType.DOUBLE)
   fun isNumberType(type: PrimitiveType) = NUMBER_TYPES.contains(type)
   fun areAllNumberTypes(types: Collection<PrimitiveType>) = types.all { isNumberType(it) }

   fun getTypeWithHighestPrecision(types: Collection<PrimitiveType>): PrimitiveType {
      require(types.isNotEmpty()) { "Cannot evaluate an empty collection" }
      return types.maxByOrNull { NUMBER_TYPES.indexOf(it) }!!
   }
}

object TemporalTypes {
   val TEMPORAL_TYPES =
      setOf(PrimitiveType.INSTANT, PrimitiveType.DATE_TIME, PrimitiveType.LOCAL_DATE, PrimitiveType.TIME)

   fun isTemporalType(type: PrimitiveType) = TEMPORAL_TYPES.contains(type)
}

interface TypeCoercer {
   fun canCoerce(value: Any): Boolean
   fun coerce(value: Any): Either<String, Any>
}

private object NoOpCoercer : TypeCoercer {
   override fun canCoerce(value: Any): Boolean = false
   override fun coerce(value: Any) = error("Not supported on NoOp Coercer")

}

private class TemporalCaster(val stringParser: (String) -> Any) : TypeCoercer {
   companion object {
      val InstantParser = { input: String ->
         val formatter = DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .optionalEnd()
            .optionalStart()
            // Accepts "Z", "+01", "+01:00", etc
            .appendOffset("+HH:MM", "Z")
            .optionalEnd()
            .optionalStart()
            .appendOffset("+HHMM", "Z")    // parses Z or +hhmm
            .optionalEnd()
            .toFormatter()
         val parsed = formatter.parseBest(
            input,
            OffsetDateTime::from,
            LocalDateTime::from
         )
         when (parsed) {
            is OffsetDateTime -> parsed.toInstant()
            is LocalDateTime -> parsed.toInstant(ZoneOffset.UTC)
            else -> error("Unhandled formatted type - can't convert ${parsed::class.simpleName} to instant")
         }
      }
   }

   override fun canCoerce(value: Any): Boolean {
      return value is String
   }

   override fun coerce(value: Any): Either<String, Any> {
      require(value is String) { "Temporal coersion only works with strings" }
      return try {
         stringParser(value).right()
      } catch (e: Exception) {
         e.message!!.left()
      }
   }
}


enum class PrimitiveType(
   val declaration: String,
   override val typeDoc: String,
   override val formatAndZoneOffset: FormatsAndZoneOffset? = null,
   private val coercer: TypeCoercer = NoOpCoercer,
   override val inheritsFrom: List<Type>,
) : Type, TypeCoercer by coercer {
   NOTHING(
      "Nothing", """`Nothing` is a special type indicating that a function or expression never returns normally,
 typically due to throwing an exception or entering an infinite loop.

 As the bottom type in the type hierarchy, `Nothing` is a subtype of all other types.
 This allows `Nothing` expressions to be assigned to any type, useful for handling exceptional cases.

 ## Characteristics:
 - **No Instances:** Represents the absence of a value.
 - **Bottom Type:** Subtype of all types, enabling flexible type assignments.
 - **Control Flow:** Used for functions that do not complete normally.

 `Nothing` ensures type-safe handling of non-returning functions and abnormal control flows.
""", inheritsFrom = emptyList()
   ),
   ANY(
      "Any",
      """
 `Any` is the root type in the Taxi type system, representing the most general type.
 All other types in Taxi inherit from `Any`, making it the universal supertype.

 ## Characteristics:
 - **Universal Supertype:** All types inherit from `Any`, making it the most general type.
 - **Extensibility:** Provides a common base for defining more specific semantic subtypes.
 - **Inheritance from Nothing:** Inherits from `Nothing`, indicating it can be the base for all types.

 ## Usage:
 While `Any` serves as the universal supertype, developers are encouraged to define and use more specific
 semantic subtypes to replace primitive types. This promotes clearer and more meaningful type definitions.

 ## Best Practices:
 - **Define Semantic Types:** Instead of using `Any` or primitive types directly, define semantic subtypes
   that convey specific meanings and constraints.
 - **Enhance Type Safety:** Using semantic types improves code readability and type safety, reducing the risk
   of errors.

 `Any` provides a flexible foundation for the Taxi type system, enabling the creation of rich and meaningful
 semantic types that enhance code clarity and reliability.
      """.trimIndent(), inheritsFrom = listOf(NOTHING)
   ),
   BOOLEAN("Boolean", "Represents a value which is either `true` or `false`.", inheritsFrom = listOf(ANY)),
   STRING("String", "A collection of characters.", inheritsFrom = listOf(ANY)),
   INTEGER(
      "Int",
      "A signed integer - ie. a whole number (positive or negative), with no decimal places",
      inheritsFrom = listOf(ANY)
   ),
   LONG(
      "Long",
      "A signed long - ie. a whole number (positive or negative), with no decimal places",
      inheritsFrom = listOf(ANY)
   ),
   DECIMAL("Decimal", "A signed decimal number - ie., a whole number with decimal places.", inheritsFrom = listOf(ANY)),
   LOCAL_DATE(
      "Date",
      "A date, without a time or timezone.",
      formatAndZoneOffset = FormatsAndZoneOffset.forFormat("yyyy-MM-dd"),
      coercer = TemporalCaster(LocalDate::parse),
      inheritsFrom = listOf(ANY)
   ),
   TIME(
      "Time",
      "Time only, excluding the date part",
      formatAndZoneOffset = FormatsAndZoneOffset.forFormat("HH:mm:ss"),
      coercer = TemporalCaster(LocalTime::parse), inheritsFrom = listOf(ANY)
   ),
   DATE_TIME(
      "DateTime",
      "A date and time, without a timezone.  Generally, favour using Instant which represents a point-in-time, as it has a timezone attached",
      formatAndZoneOffset = FormatsAndZoneOffset.forFormat("yyyy-MM-dd'T'HH:mm:ss.SSS"),
      coercer = TemporalCaster(LocalDateTime::parse), inheritsFrom = listOf(ANY)
   ),
   INSTANT(
      "Instant",
      "A point in time, with date, time and timezone.  Follows ISO standard convention of yyyy-MM-dd'T'HH:mm:ss.SSSZ",
      formatAndZoneOffset = FormatsAndZoneOffset.forFormat("yyyy-MM-dd'T'HH:mm:ss[.SSS]X"),
      coercer = TemporalCaster(TemporalCaster.InstantParser), inheritsFrom = listOf(ANY)
   ),

   //   ARRAY("Array", "A collection of things"),

   DOUBLE("Double", "Represents a double-precision 64-bit IEEE 754 floating point number.", inheritsFrom = listOf(ANY)),
   VOID(
      "Void",
      "Nothing.  Represents the return value of operations that don't return anything.",
      inheritsFrom = listOf(ANY)
   );

//    Used in things like jsonPath(), or xPath() where a type isn't declared
//   UNTYPED("Untyped", "A special internal type for functions that don't declare a return type.  Untyped things can be assigned to anything, and all bets are off until runtime. Don't use this - it's not for you.");

   private val wrapper = LazyLoadingWrapper(this)
   override val allInheritedTypes: Set<Type> by lazy { wrapper.allInheritedTypes }
   override val inheritsFromPrimitive: Boolean by lazy { wrapper.inheritsFromPrimitive }
   override val basePrimitive: PrimitiveType? by lazy { wrapper.basePrimitive }
   override val definitionHash: String? by lazy { wrapper.definitionHash }

   override val isScalar: Boolean = true

   override val format: List<String>? = formatAndZoneOffset?.patterns
   override val offset: Int? = formatAndZoneOffset?.utcZoneOffsetInMinutes

   // Not currently implemented, but could be in the future
   override val annotations: List<lang.taxi.types.Annotation> = emptyList()


   override val qualifiedName: String
      get() = "$NAMESPACE.$declaration"

   override val compilationUnits: List<CompilationUnit> =
      listOf(CompilationUnit.ofSource(SourceCode("native.taxi", "// Built-in type")))

   override val typeKind: TypeKind = TypeKind.Type

   companion object {
      private val typesByName = values().associateBy { it.declaration }
      private val typesByQualifiedName: Map<String, PrimitiveType> = values().associateBy { it.qualifiedName }
      private val typesByLookup = typesByName + typesByQualifiedName

      private val enumSet = EnumSet.allOf(PrimitiveType::class.java)

      const val NAMESPACE = "lang.taxi"

      val INHERITS_FROM_ANY = listOf(ANY)
      val NUMBER_TYPES = NumberTypes.NUMBER_TYPES
      fun fromDeclaration(value: String): PrimitiveType {
         return typesByLookup[value] ?: throw IllegalArgumentException("$value is not a valid primative")
      }

//        fun fromToken(typeToken: TaxiParser.TypeReferenceContext): PrimitiveType {
//            return fromDeclaration(typeToken.primitiveType()!!.text)
//        }

      @Deprecated(replaceWith = ReplaceWith("ArrayType.isTypedCollection"), message = "Deprecated")
      fun isTypedCollection(qualifiedName: QualifiedName): Boolean {
         return ArrayType.isTypedCollection(qualifiedName)
      }

      fun isNumberType(type: Type): Boolean {
         return type.basePrimitive != null && NUMBER_TYPES.contains(type.basePrimitive)
      }

      fun isPrimitiveType(qualifiedName: String): Boolean {
         return typesByLookup.containsKey(qualifiedName)
      }

      fun isPrimitiveType(type: Type): Boolean {
         return enumSet.contains(type)
      }

      fun isAssignableToPrimitiveType(type: Type): Boolean {
         return getUnderlyingPrimitiveIfExists(type) != null
      }

      fun getPrimitive(name: String): PrimitiveType {
         return typesByLookup[name] ?: error("Type $name is not a primitive type")
      }

      fun getUnderlyingPrimitive(type: Type): PrimitiveType {
         return getUnderlyingPrimitiveIfExists(type) as PrimitiveType?
            ?: error("Type ${type.qualifiedName} is not mappable to a primitive type")
      }

      private fun getUnderlyingPrimitiveIfExists(type: Type): Type? {
         val primitiveCandidates = getAllUnderlyingPrimitiveIfExists(type)
         return when {
            primitiveCandidates.isEmpty() -> null
            primitiveCandidates.size > 1 -> error("Type ${type.qualifiedName} ambiguously maps to multiple primitive types: ${primitiveCandidates.joinToString { it.qualifiedName }}")
            else -> primitiveCandidates.first()
         }
      }

      private fun getAllUnderlyingPrimitiveIfExists(type: Type, typesToIgnore: Set<Type> = emptySet()): Set<Type> {
         if (type is PrimitiveType || isPrimitiveType(type.qualifiedName)) {
            return setOf(type)
         }
         if (type is TypeAlias) {
            return getAllUnderlyingPrimitiveIfExists(type.aliasType!!, typesToIgnore + type.aliasType!!)
         }
         val recursiveTypesToIgnore = typesToIgnore + type
         return type.allInheritedTypes.flatMap { getAllUnderlyingPrimitiveIfExists(it, recursiveTypesToIgnore) }.toSet()
      }

      fun mostSpecificPrimitive(types: Collection<Type>): PrimitiveType? {
         return types.filter { PrimitiveType.isPrimitiveType(it) }
            .maxByOrNull { it.allInheritedTypes.size } as PrimitiveType?
      }
   }
}

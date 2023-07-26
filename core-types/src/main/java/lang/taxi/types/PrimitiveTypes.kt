package lang.taxi.types

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import lang.taxi.sources.SourceCode
import java.time.*
import java.time.format.DateTimeFormatter


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
   override val inheritsFrom: Set<Type> = emptySet()
   override val format: List<String>? = null
   override val formatAndZoneOffset: FormatsAndZoneOffset? = null
   override val offset: Int? = null
   override val typeKind: TypeKind = TypeKind.Type

   // Not currently implemented, but could be in the future
   override val annotations: List<lang.taxi.types.Annotation> = emptyList()

}

object NumberTypes {
   // Order is important here, list the number types in
   // order of specificity.
   // Numeric expressions ensure that the return type
   // is the most specific
   val NUMBER_TYPES = listOf(PrimitiveType.INTEGER, PrimitiveType.DECIMAL, PrimitiveType.DOUBLE)
   fun isNumberType(type: PrimitiveType) = NUMBER_TYPES.contains(type)
   fun areAllNumberTypes(types: Collection<PrimitiveType>) = types.all { isNumberType(it) }

   fun getTypeWithHightestPrecision(types: Collection<PrimitiveType>): PrimitiveType {
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
         val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS][X]")
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
   private val coercer: TypeCoercer = NoOpCoercer
) : Type, TypeCoercer by coercer {
   BOOLEAN("Boolean", "Represents a value which is either `true` or `false`."),
   STRING("String", "A collection of characters."),
   INTEGER("Int", "A signed integer - ie. a whole number (positive or negative), with no decimal places"),
   DECIMAL("Decimal", "A signed decimal number - ie., a whole number with decimal places."),
   LOCAL_DATE(
      "Date",
      "A date, without a time or timezone.",
      formatAndZoneOffset = FormatsAndZoneOffset.forFormat("yyyy-MM-dd"),
      coercer = TemporalCaster(LocalDate::parse)
   ),
   TIME(
      "Time",
      "Time only, excluding the date part",
      formatAndZoneOffset = FormatsAndZoneOffset.forFormat("HH:mm:ss"),
      coercer = TemporalCaster(LocalTime::parse)
   ),
   DATE_TIME(
      "DateTime",
      "A date and time, without a timezone.  Generally, favour using Instant which represents a point-in-time, as it has a timezone attached",
      formatAndZoneOffset = FormatsAndZoneOffset.forFormat("yyyy-MM-dd'T'HH:mm:ss.SSS"),
      coercer = TemporalCaster(LocalDateTime::parse)
   ),
   INSTANT(
      "Instant",
      "A point in time, with date, time and timezone.  Follows ISO standard convention of yyyy-MM-dd'T'HH:mm:ss.SSSZ",
      formatAndZoneOffset = FormatsAndZoneOffset.forFormat("yyyy-MM-dd'T'HH:mm:ss[.SSS]X"),
      coercer = TemporalCaster(TemporalCaster.InstantParser)
   ),

   //   ARRAY("Array", "A collection of things"),
   ANY(
      "Any",
      "Can be anything.  Try to avoid using 'Any' as it's not descriptive - favour using a strongly typed approach instead"
   ),
   DOUBLE("Double", "Represents a double-precision 64-bit IEEE 754 floating point number."),
   VOID("Void", "Nothing.  Represents the return value of operations that don't return anything.");

//    Used in things like jsonPath(), or xPath() where a type isn't declared
//   UNTYPED("Untyped", "A special internal type for functions that don't declare a return type.  Untyped things can be assigned to anything, and all bets are off until runtime. Don't use this - it's not for you.");

   private val wrapper = LazyLoadingWrapper(this)
   override val allInheritedTypes: Set<Type> by lazy { wrapper.allInheritedTypes }
   override val inheritsFromPrimitive: Boolean by lazy { wrapper.inheritsFromPrimitive }
   override val basePrimitive: PrimitiveType? by lazy { wrapper.basePrimitive }
   override val definitionHash: String? by lazy { wrapper.definitionHash }

   override val format: List<String>? = formatAndZoneOffset?.patterns
   override val offset: Int? = formatAndZoneOffset?.utcZoneOffsetInMinutes

   // Not currently implemented, but could be in the future
   override val annotations: List<lang.taxi.types.Annotation> = emptyList()


   override val qualifiedName: String
      get() = "$NAMESPACE.$declaration"

   override val inheritsFrom: Set<Type> = emptySet()

   override val compilationUnits: List<CompilationUnit> =
      listOf(CompilationUnit.ofSource(SourceCode("native.taxi", "// Built-in type")))

   override val typeKind: TypeKind = TypeKind.Type

   companion object {
      private val typesByName = values().associateBy { it.declaration }
      private val typesByQualifiedName = values().associateBy { it.qualifiedName }
      private val typesByLookup = typesByName + typesByQualifiedName

      const val NAMESPACE = "lang.taxi"




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

      fun isNumberType(type: Type):Boolean {
         return type.basePrimitive != null && NUMBER_TYPES.contains(type.basePrimitive)
      }

      fun isPrimitiveType(qualifiedName: String): Boolean {
         return typesByLookup.containsKey(qualifiedName)
      }

      fun isAssignableToPrimitiveType(type: Type): Boolean {
         return getUnderlyingPrimitiveIfExists(type) != null
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
   }
}

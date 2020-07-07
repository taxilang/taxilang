package lang.taxi.types

import lang.taxi.sources.SourceCode


enum class VoidType : Type {
   VOID;

   private val wrapper = LazyLoadingWrapper(this)
   override val allInheritedTypes: Set<Type> by lazy { wrapper.allInheritedTypes }
   override val inheritsFromPrimitive: Boolean by lazy { wrapper.inheritsFromPrimitive }
   override val basePrimitive: PrimitiveType? by lazy { wrapper.basePrimitive }
   override val definitionHash: String? by lazy { wrapper.definitionHash }

   override val qualifiedName: String = "lang.taxi.Void"
   override val compilationUnits: List<CompilationUnit> = listOf(CompilationUnit.ofSource(SourceCode("Built in", "// Built-in type")))
   override val inheritsFrom: Set<Type> = emptySet()
   override val format: String? = null
   override val formattedInstanceOfType: Type? = null
}

enum class PrimitiveType(val declaration: String, val typeDoc: String, override val format: String? = null, override val formattedInstanceOfType: Type? = null) : Type {
   BOOLEAN("Boolean", "Represents a value which is either `true` or `false`."),
   STRING("String", "A collection of characters."),
   INTEGER("Int", "A signed integer - ie. a whole number (positive or negative), with no decimal places"),
   DECIMAL("Decimal", "A signed decimal number - ie., a whole number with decimal places."),
   LOCAL_DATE("Date", "A date, without a time or timezone.", format = "yyyy-MM-dd"),
   TIME("Time", "Time only, excluding the date part", format = "HH:mm:ss"),
   DATE_TIME("DateTime", "A date and time, without a timezone.  Generally, favour using Instant which represents a point-in-time, as it has a timezone attached", format = "yyyy-MM-dd'T'HH:mm:ss.SSS"),
   INSTANT("Instant", "A point in time, with date, time and timezone.  Follows ISO standard convention of yyyy-MM-dd'T'HH:mm:ss.SSSZ", format = "yyyy-MM-dd'T'HH:mm:ss[.SSS]X"),
   ARRAY("Array", "A collection of things"),
   ANY("Any", "Can be anything.  Try to avoid using 'Any' as it's not descriptive - favour using a strongly typed approach instead"),
   DOUBLE("Double", "Represents a double-precision 64-bit IEEE 754 floating point number."),
   VOID("Void", "Nothing.  Represents the return value of operations that don't return anything.");

   private val wrapper = LazyLoadingWrapper(this)
   override val allInheritedTypes: Set<Type> by lazy { wrapper.allInheritedTypes }
   override val inheritsFromPrimitive: Boolean by lazy { wrapper.inheritsFromPrimitive }
   override val basePrimitive: PrimitiveType? by lazy { wrapper.basePrimitive }
   override val definitionHash: String? by lazy { wrapper.definitionHash }

   override val qualifiedName: String
      get() = "lang.taxi.$declaration"

   override val inheritsFrom: Set<Type> = emptySet()

   override val compilationUnits: List<CompilationUnit> = listOf(CompilationUnit.ofSource(SourceCode("native.taxi", "// Built-in type")))

   companion object {
      private val typesByName = values().associateBy { it.declaration }
      private val typesByQualifiedName = values().associateBy { it.qualifiedName }
      private val typesByLookup = typesByName + typesByQualifiedName

      fun fromDeclaration(value: String): PrimitiveType {
         return typesByLookup[value] ?: throw IllegalArgumentException("$value is not a valid primative")
      }

//        fun fromToken(typeToken: TaxiParser.TypeTypeContext): PrimitiveType {
//            return fromDeclaration(typeToken.primitiveType()!!.text)
//        }

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

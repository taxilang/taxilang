package lang.taxi.types

import lang.taxi.sources.SourceCode


enum class VoidType : Type {
   VOID;

   private val wrapper = LazyLoadingWrapper(this)
   override val allInheritedTypes: Set<Type> by lazy { wrapper.allInheritedTypes }
   override val inheritsFromPrimitive: Boolean by lazy { wrapper.inheritsFromPrimitive }
   override val basePrimitive: PrimitiveType? by lazy { wrapper.basePrimitive }
   override val definitionHash: String? by lazy { wrapper.definitionHash }
   override val typeDoc: String = "Nothing"

   override val qualifiedName: String = "lang.taxi.Void"
   override val compilationUnits: List<CompilationUnit> = listOf(CompilationUnit.ofSource(SourceCode("Built in", "// Built-in type")))
   override val inheritsFrom: Set<Type> = emptySet()
   override val format: List<String>? = null
   override val formattedInstanceOfType: Type? = null
   override val calculation: Formula?
      get() = null
   override val offset: Int? = null
   override val typeKind: TypeKind = TypeKind.Type
}

object NumberTypes {
   // Order is important here, list the number types in
   // order of specificity.
   // Numeric expressions ensure that the return type
   // is the most specific
   val NUMBER_TYPES = listOf(PrimitiveType.INTEGER, PrimitiveType.DECIMAL, PrimitiveType.DOUBLE)
   fun isNumberType(type:PrimitiveType) = NUMBER_TYPES.contains(type)
   fun areAllNumberTypes(types:Collection<PrimitiveType>) = types.all { isNumberType(it) }

   fun getTypeWithHightestPrecision(types:Collection<PrimitiveType>):PrimitiveType {
      require(types.isNotEmpty()) { "Cannot evaluate an empty collection"}
      return types.maxByOrNull { NUMBER_TYPES.indexOf(it) }!!
   }
}
enum class PrimitiveType(
   val declaration: String,
   override val typeDoc: String,
   override val format: List<String>? = null,
   override val formattedInstanceOfType: Type? = null,
   override val calculation: Formula? = null,
   override val offset: Int? = null) : Type {
   BOOLEAN("Boolean", "Represents a value which is either `true` or `false`."),
   STRING("String", "A collection of characters."),
   INTEGER("Int", "A signed integer - ie. a whole number (positive or negative), with no decimal places"),
   DECIMAL("Decimal", "A signed decimal number - ie., a whole number with decimal places."),
   LOCAL_DATE("Date", "A date, without a time or timezone.", format = listOf("yyyy-MM-dd" )),
   TIME("Time", "Time only, excluding the date part", format = listOf("HH:mm:ss" )),
   DATE_TIME("DateTime", "A date and time, without a timezone.  Generally, favour using Instant which represents a point-in-time, as it has a timezone attached", format = listOf("yyyy-MM-dd'T'HH:mm:ss.SSS" )),
   INSTANT("Instant", "A point in time, with date, time and timezone.  Follows ISO standard convention of yyyy-MM-dd'T'HH:mm:ss.SSSZ", format = listOf("yyyy-MM-dd'T'HH:mm:ss[.SSS]X" )),
//   ARRAY("Array", "A collection of things"),
   ANY("Any", "Can be anything.  Try to avoid using 'Any' as it's not descriptive - favour using a strongly typed approach instead"),
   DOUBLE("Double", "Represents a double-precision 64-bit IEEE 754 floating point number."),
   VOID("Void", "Nothing.  Represents the return value of operations that don't return anything.");

//    Used in things like jsonPath(), or xPath() where a type isn't declared
//   UNTYPED("Untyped", "A special internal type for functions that don't declare a return type.  Untyped things can be assigned to anything, and all bets are off until runtime. Don't use this - it's not for you.");

   private val wrapper = LazyLoadingWrapper(this)
   override val allInheritedTypes: Set<Type> by lazy { wrapper.allInheritedTypes }
   override val inheritsFromPrimitive: Boolean by lazy { wrapper.inheritsFromPrimitive }
   override val basePrimitive: PrimitiveType? by lazy { wrapper.basePrimitive }
   override val definitionHash: String? by lazy { wrapper.definitionHash }

   override val qualifiedName: String
      get() = "$NAMESPACE.$declaration"

   override val inheritsFrom: Set<Type> = emptySet()

   override val compilationUnits: List<CompilationUnit> = listOf(CompilationUnit.ofSource(SourceCode("native.taxi", "// Built-in type")))

   override val typeKind: TypeKind = TypeKind.Type

   companion object {
      private val typesByName = values().associateBy { it.declaration }
      private val typesByQualifiedName = values().associateBy { it.qualifiedName }
      private val typesByLookup = typesByName + typesByQualifiedName

      const val NAMESPACE = "lang.taxi"


      val NUMBER_TYPES =NumberTypes.NUMBER_TYPES
      fun fromDeclaration(value: String): PrimitiveType {
         return typesByLookup[value] ?: throw IllegalArgumentException("$value is not a valid primative")
      }

//        fun fromToken(typeToken: TaxiParser.TypeTypeContext): PrimitiveType {
//            return fromDeclaration(typeToken.primitiveType()!!.text)
//        }

      @Deprecated(replaceWith = ReplaceWith("ArrayType.isTypedCollection"), message = "Deprecated")
      fun isTypedCollection(qualifiedName: QualifiedName):Boolean {
         return ArrayType.isTypedCollection(qualifiedName)
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

object PrimitiveTypeOperations {
   private val allSupportedOperations: Map<PrimitiveType, Map<FormulaOperator, List<PrimitiveType>>>

   init {
      val numericOperations = PrimitiveType.NUMBER_TYPES.map { numberType ->
         numberType to mapOf(
            FormulaOperator.Add to PrimitiveType.NUMBER_TYPES,
            FormulaOperator.Subtract to PrimitiveType.NUMBER_TYPES,
            FormulaOperator.Multiply to PrimitiveType.NUMBER_TYPES,
            FormulaOperator.Divide to PrimitiveType.NUMBER_TYPES
         )
      }.toMap()

      val dateTimeOperations = mapOf(PrimitiveType.LOCAL_DATE to mapOf(
         FormulaOperator.Add to listOf(PrimitiveType.TIME)
      ))

      val stringOperations = mapOf(PrimitiveType.STRING to mapOf(
         FormulaOperator.Add to listOf(PrimitiveType.STRING)
      ))
      allSupportedOperations = numericOperations + dateTimeOperations + stringOperations
   }

   fun isValidOperation(firstOperand: PrimitiveType, operator: FormulaOperator, secondOperand: PrimitiveType): Boolean {
      val supportedOperations = allSupportedOperations[firstOperand] ?: emptyMap()
      val supportedTargetTypes = supportedOperations[operator] ?: emptyList()
      return supportedTargetTypes.contains(secondOperand)
   }

}

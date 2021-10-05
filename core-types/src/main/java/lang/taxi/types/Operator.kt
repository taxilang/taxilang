package lang.taxi.types


private fun areAllBoolean(): (Set<PrimitiveType>) -> Boolean = onlyContainsType(PrimitiveType.BOOLEAN)
private fun onlyNumericTypes(): (Set<PrimitiveType>) -> Boolean {
   return { types ->
      types.all { NumberTypes.isNumberType(it) }
   }
}

private fun areAllSameType(): (Set<PrimitiveType>) -> Boolean {
   return { types -> types.size == 1 }
}

private fun onlyContainsType(type: PrimitiveType): (Set<PrimitiveType>) -> Boolean {
   return { types ->
      types.all { it == type }
   }
}

private fun containsExactly(typeA: PrimitiveType, typeB: PrimitiveType): (Set<PrimitiveType>) -> Boolean {
   return { types ->
      types == setOf(typeA, typeB)
   }
}

enum class FormulaOperator(
   val symbol: String,
   /**
    * Determines the combinations of primitive types where an operator
    * can be applied.
    *
    * The operator can be applied if ANY of the predicates return true.
    * (ie., these are an 'OR', not an 'AND')
    */
   private val supportedOperandPredicates: List<(Set<PrimitiveType>) -> Boolean>
) {
   Add(
      "+", listOf(
         onlyNumericTypes(),
         onlyContainsType(PrimitiveType.STRING),
         containsExactly(PrimitiveType.LOCAL_DATE, PrimitiveType.TIME)
      )
   ),
   Subtract("-", listOf(onlyNumericTypes())),
   Multiply("*", listOf(onlyNumericTypes())),
   Divide("/", listOf(onlyNumericTypes())),
   GreaterThan(">", listOf(onlyNumericTypes())),
   LessThan("<", listOf(onlyNumericTypes())),
   GreaterThanOrEqual(">=", listOf(onlyNumericTypes())),
   LessThanOrEqual("<=", listOf(onlyNumericTypes())),
   LogicalAnd("&&", listOf(areAllBoolean())),
   LogicalOr("||", listOf(areAllBoolean())),
   Equal("==", listOf(areAllSameType())),
   NotEqual("!=", listOf(areAllSameType()));


   fun isLogicalOperator(): Boolean = LOGICAL_OPERATORS.contains(this)
   fun supportsNullComparison():Boolean {
      return this == Equal || this == NotEqual
   }

   fun supports(lhsType:PrimitiveType, rhsType:PrimitiveType):Boolean {
      val types = setOf(lhsType,rhsType)
      return this.supportedOperandPredicates.any { predicate -> predicate(types) }
   }

   companion object {

      val LOGICAL_OPERATORS = setOf(
         GreaterThan,
         GreaterThanOrEqual,
         LessThan,
         LessThanOrEqual,
         LogicalAnd,
         LogicalOr,
         Equal,
         NotEqual,
      )
      private val bySymbol = values().associateBy { it.symbol }
      fun forSymbol(symbol: String): FormulaOperator {
         return bySymbol[symbol] ?: error("No operator defined for symbol $symbol")
      }

      fun isSymbol(symbol: String): Boolean {
         return bySymbol.containsKey(symbol)
      }
   }
}


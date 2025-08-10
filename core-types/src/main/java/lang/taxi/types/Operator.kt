package lang.taxi.types


private fun areAllBoolean(): (List<Type>) -> Boolean = onlyContainsType(PrimitiveType.BOOLEAN)
private fun onlyNumericTypes(): (List<Type>) -> Boolean {
   return { types ->
      types.all { it is PrimitiveType && NumberTypes.isNumberType(it) }
   }
}

private fun comparableTypes(): (List<Type>) -> Boolean {
   return { types ->
      types.all { it is PrimitiveType && (NumberTypes.isNumberType(it) || TemporalTypes.isTemporalType(it)) }
   }
}

private fun areAllSameType(): (List<Type>) -> Boolean {
   return { types -> types.toSet().size == 1 }
}

private fun allow(): (List<Type>) -> Boolean {
   return { true }
}

private fun onlyContainsType(type: PrimitiveType): (List<Type>) -> Boolean {
   return { types ->
      types.all { it == type }
   }
}

private fun isValidInOperation(): (List<Type>) -> Boolean {
   return { inputTypes ->
      inputTypes.size == 2 && inputTypes[0].isScalar && Arrays.isArray(inputTypes[1])
         && inputTypes[0].isAssignableTo(Arrays.unwrapPossibleArrayType(inputTypes[1]))
   }

}

private fun containsExactly(typeA: PrimitiveType, typeB: PrimitiveType): (List<Type>) -> Boolean {
   return { types ->
      types.toSet() == setOf(typeA, typeB)
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
   private val supportedOperandPredicates: List<(List<Type>) -> Boolean>
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
   Modulo("%", listOf(onlyNumericTypes())),
   GreaterThan(">", listOf(comparableTypes())),
   LessThan("<", listOf(comparableTypes())),
   GreaterThanOrEqual(">=", listOf(comparableTypes())),
   LessThanOrEqual("<=", listOf(comparableTypes())),
   LogicalAnd("&&", listOf(areAllBoolean())),
   LogicalOr("||", listOf(areAllBoolean())),
   Equal("==", listOf(areAllSameType(), comparableTypes())),
   NotEqual("!=", listOf(areAllSameType())),
   Coalesce("?:", listOf(allow())),
   In("in", listOf(isValidInOperation())),
   NotIn("not in", listOf(isValidInOperation())),
   ;

   fun isLogicalOperator(): Boolean = LOGICAL_OPERATORS.contains(this)
   fun isComparisonOperator(): Boolean = COMPARISON_OPERATORS.contains(this)
   fun isLogicalOrComparisonOperator(): Boolean = isLogicalOperator() || isComparisonOperator()
   fun supportsNullComparison(): Boolean {
      return this == Equal || this == NotEqual
   }

   fun supports(lhsType: Type, rhsType: Type): Boolean {
      val types = listOf(lhsType, rhsType)
      return this.supportedOperandPredicates.any { predicate -> predicate(types) }
   }

   companion object {

      val LOGICAL_OPERATORS = setOf(
         LogicalAnd,
         LogicalOr,
      )

      val COMPARISON_OPERATORS = setOf(
         GreaterThan,
         GreaterThanOrEqual,
         LessThan,
         LessThanOrEqual,
         Equal,
         NotEqual,
         In,
         NotIn
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


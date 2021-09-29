package lang.taxi.types

@Deprecated("Formulas are replaced by functons and expressions")
interface Formula : TaxiStatementGenerator {
   val operandFields: List<QualifiedName>
   val operator: FormulaOperator

   override fun asTaxi(): String {
      return when (operator) {
         // Note: Coalesce shouldn't have been implemented as a formula, but as an accessor.
         // Will need to fix this.
         FormulaOperator.Coalesce -> "as ${operator.symbol}(${operandFields.joinToString { it.fullyQualifiedName }})"
         else -> "as (${operandFields.joinToString(" ${operator.symbol} ") { it.fullyQualifiedName }} )"
      }
   }
}

data class OperatorFormula(
   override val operandFields: List<QualifiedName>,
   override val operator: FormulaOperator
) : Formula

@Deprecated("Use OperatorFormula instead")
data class MultiplicationFormula(
   override val operandFields: List<QualifiedName>,
   override val operator: FormulaOperator = FormulaOperator.Multiply
) : Formula {
}

enum class FormulaOperator(val symbol: String, @Deprecated("this concept doesn't work") val cardinality: Int = -1) {
   Add("+", 2) {
      override fun validArgumentSize(argumentSize: Int): Boolean {
         return argumentSize == cardinality
      }

      override fun validateArguments(arguments: List<PrimitiveType>, fieldPrimitiveType: PrimitiveType): Boolean {
         return PrimitiveTypeOperations.isValidOperation(arguments.first(), Add, arguments[1])
      }
   },
   Subtract("-", 2) {
      override fun validArgumentSize(argumentSize: Int): Boolean {
         return argumentSize == cardinality
      }

      override fun validateArguments(arguments: List<PrimitiveType>, fieldPrimitiveType: PrimitiveType): Boolean {
         return PrimitiveTypeOperations.isValidOperation(arguments.first(), Subtract, arguments[1])
      }
   },
   Multiply("*", 2) {
      override fun validArgumentSize(argumentSize: Int): Boolean {
         return argumentSize == cardinality
      }

      override fun validateArguments(arguments: List<PrimitiveType>, fieldPrimitiveType: PrimitiveType): Boolean {
         return PrimitiveTypeOperations.isValidOperation(arguments.first(), Multiply, arguments[1])
      }
   },
   Divide("/", 2) {
      override fun validArgumentSize(argumentSize: Int): Boolean {
         return argumentSize == cardinality
      }

      override fun validateArguments(arguments: List<PrimitiveType>, fieldPrimitiveType: PrimitiveType): Boolean {
         return PrimitiveTypeOperations.isValidOperation(arguments.first(), Divide, arguments[1])
      }
   },
   GreaterThan(">"),
   LessThan("<"),
   GreaterThanOrEqual(">="),
   LessThanOrEqual("<="),
   LogicalAnd("&&"),
   LogicalOr("||"),
   Equal("=="),
   NotEqual("!="),

   // TODO : Coalesce should really be an accessor, not a formula
   @Deprecated("Moved to function")
   Coalesce("coalesce", Int.MAX_VALUE) {
      override fun validArgumentSize(argumentSize: Int): Boolean {
         return argumentSize >= 1
      }

      override fun validateArguments(arguments: List<PrimitiveType>, fieldPrimitiveType: PrimitiveType): Boolean {
         val types = arguments.map { it.qualifiedName }.distinct()
         return types.size == 1 && types.first() == fieldPrimitiveType.qualifiedName
      }
   };

   @Deprecated("This doesn't make any sense, as math operations can have n arguments")
   open fun validArgumentSize(argumentSize: Int): Boolean {
      return true
   }

   open fun validateArguments(arguments: List<PrimitiveType>, fieldPrimitiveType: PrimitiveType): Boolean {
      return true
   }

   fun isLogicalOperator():Boolean = LOGICAL_OPERATORS.contains(this)

   companion object {
      val LOGICAL_OPERATORS = setOf(
         FormulaOperator.GreaterThan,
         FormulaOperator.GreaterThanOrEqual,
         FormulaOperator.LessThan,
         FormulaOperator.LessThanOrEqual,
         FormulaOperator.LogicalAnd,
         FormulaOperator.LogicalOr,
         FormulaOperator.Equal,
         FormulaOperator.NotEqual,
      )
      private val bySymbol = FormulaOperator.values().associateBy { it.symbol }
      fun forSymbol(symbol: String): FormulaOperator {
         return bySymbol[symbol] ?: error("No operator defined for symbol $symbol")
      }

      fun isSymbol(symbol: String): Boolean {
         return bySymbol.containsKey(symbol)
      }
   }
}




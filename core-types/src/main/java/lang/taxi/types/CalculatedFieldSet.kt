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
   override val operator: FormulaOperator = FormulaOperator.Multiply) : Formula {
}

enum class FormulaOperator(val symbol: String, @Deprecated("this concept doesn't work") val cardinality: Int) {
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
   // TODO : Coalesce should really be an accessor, not a formula
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
   abstract fun validArgumentSize(argumentSize: Int): Boolean;
   abstract fun validateArguments(arguments: List<PrimitiveType>, fieldPrimitiveType: PrimitiveType): Boolean;

   companion object {
      private val bySymbol = FormulaOperator.values().associateBy { it.symbol }
      fun forSymbol(symbol: String): FormulaOperator {
         return bySymbol[symbol] ?: error("No operator defined for symbol $symbol")
      }
      fun isSymbol(symbol: String): Boolean {
         return bySymbol.containsKey(symbol)
      }
   }
}




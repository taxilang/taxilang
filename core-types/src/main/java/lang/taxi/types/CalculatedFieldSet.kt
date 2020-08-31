package lang.taxi.types

interface Formula : TaxiStatementGenerator {
   val operandFields: List<QualifiedName>
   val operator: FormulaOperator

   override fun asTaxi(): String {
      return "as (${operandFields.joinToString(" ${operator.symbol} ") { it.fullyQualifiedName }} )"
   }
}

data class OperatorFormula(
   override val operandFields:List<QualifiedName>,
   override val operator:FormulaOperator
) : Formula

@Deprecated("Use OperatorFormula instead")
data class MultiplicationFormula(
   override val operandFields: List<QualifiedName>,
   override val operator: FormulaOperator = FormulaOperator.Multiply): Formula {
}

enum class FormulaOperator(val symbol:String, val cardinality: Int) {
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
   Coalesce("coalesce", Int.MAX_VALUE) {
      override fun validArgumentSize(argumentSize: Int): Boolean {
         return argumentSize >= 1
      }

      override fun validateArguments(arguments: List<PrimitiveType>, fieldPrimitiveType: PrimitiveType): Boolean {
         val types = arguments.map { it.qualifiedName }.distinct()
         return types.size == 1 && types.first() == fieldPrimitiveType.qualifiedName
      }
   };

   abstract fun validArgumentSize(argumentSize: Int): Boolean;
   abstract fun validateArguments(arguments: List<PrimitiveType>, fieldPrimitiveType: PrimitiveType): Boolean;

   companion object {
      private val bySymbol = FormulaOperator.values().associateBy { it.symbol }
      fun forSymbol(symbol:String):FormulaOperator {
         return bySymbol[symbol] ?: error("No operator defined for symbol $symbol")
      }
   }
}

enum class UnaryFormulaOperator(val symbol: String) {
   Left("left");
   companion object {
      private val bySymbol = UnaryFormulaOperator.values().associateBy { it.symbol }
      fun forSymbol(symbol:String):UnaryFormulaOperator {
         return bySymbol[symbol] ?: error("No operator defined for symbol $symbol")
      }

      fun forSymbolOrNull(symbol: String) = bySymbol[symbol]
   }
}

enum class TerenaryFormulaOperator(val symbol: String) {
   Concat3("concat3");
   companion object {
      private val bySymbol = TerenaryFormulaOperator.values().associateBy { it.symbol }
      fun forSymbol(symbol:String):TerenaryFormulaOperator {
         return bySymbol[symbol] ?: error("No operator defined for symbol $symbol")
      }

      fun forSymbolOrNull(symbol: String) = bySymbol[symbol]
   }
}



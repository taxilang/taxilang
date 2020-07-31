package lang.taxi.types

interface Formula {
   val operandFields: List<QualifiedName>
   val operator: FormulaOperator
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

enum class FormulaOperator(val symbol:String) {
   Add("+"),
   Subtract("-"),
   Multiply("*"),
   Divide("/");

   companion object {
      private val bySymbol = FormulaOperator.values().associateBy { it.symbol }
      fun forSymbol(symbol:String):FormulaOperator {
         return bySymbol[symbol] ?: error("No operator defined for symbol $symbol")
      }
   }
}


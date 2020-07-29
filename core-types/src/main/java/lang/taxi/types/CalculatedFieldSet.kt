package lang.taxi.types

interface Formula {
   val operandFields: List<QualifiedName>
   val operator: FormulaOperator
}

data class MultiplicationFormula(
   override val operandFields: List<QualifiedName>,
   override val operator: FormulaOperator = FormulaOperator.Multiply): Formula {
}

enum class FormulaOperator {
   Multiply
}


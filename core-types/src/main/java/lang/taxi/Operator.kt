package lang.taxi

import lang.taxi.types.FormulaOperator

@Deprecated("Use FormulaOperator instead")
enum class Operator(val symbol: String) {
   EQUAL("=="),
   NOT_EQUAL("!="),
   IN("in"),
   LIKE("like"),
   GREATER_THAN(">"),
   LESS_THAN("<"),
   GREATER_THAN_OR_EQUAL_TO(">="),
   LESS_THAN_OR_EQUAL_TO("<=");

   companion object {
      private val symbols = Operator.values().associateBy { it.symbol }
      fun parse(value: String): Operator {
         return symbols[value] ?: error("No operator matches symbol $value")
      }
   }

   fun asFormulaOperator():FormulaOperator {
      return when (this) {
         EQUAL -> FormulaOperator.Equal
         NOT_EQUAL -> FormulaOperator.NotEqual
         GREATER_THAN -> FormulaOperator.GreaterThan
         GREATER_THAN_OR_EQUAL_TO -> FormulaOperator.GreaterThanOrEqual
         LESS_THAN -> FormulaOperator.LessThan
         LESS_THAN_OR_EQUAL_TO -> FormulaOperator.LessThanOrEqual
         else -> error("Operator ${this.name} does not have an equivalent FormulaOperator")
      }
   }
}

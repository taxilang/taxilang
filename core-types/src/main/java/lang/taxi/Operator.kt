package lang.taxi

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
}

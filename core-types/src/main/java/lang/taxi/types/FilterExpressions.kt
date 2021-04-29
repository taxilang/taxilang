package lang.taxi.types

import java.util.logging.Filter

interface FilterExpression: TaxiStatementGenerator
data class FilterExpressionInParenthesis(val containedExpression: FilterExpression): FilterExpression {
   override fun asTaxi(): String {
      return "(${containedExpression.asTaxi()})"
   }
}

data class InFilterExpression(val values: List<Any>, val type: Type): FilterExpression {
   override fun asTaxi(): String {
      return "${type.qualifiedName} in [${values.map {
         when(it) {
            is String -> "'$it'"
            else -> "$it"
         }
      }}]"
   }
}

data class  LikeFilterExpression(val value: String, val type: Type): FilterExpression {
   override fun asTaxi(): String {
      return "${type.qualifiedName} like '$value'"
   }
}

data class OrFilterExpression(val filterLeft: FilterExpression, val filterRight: FilterExpression): FilterExpression {
   override fun asTaxi(): String {
      return "${filterLeft.asTaxi()} or ${filterRight.asTaxi()}"
   }
}

data class AndFilterExpression(val filterLeft: FilterExpression, val filterRight: FilterExpression): FilterExpression {
   override fun asTaxi(): String {
      return "${filterLeft.asTaxi()} and ${filterRight.asTaxi()}"
   }
}



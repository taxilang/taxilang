package lang.taxi.types

import lang.taxi.utils.quoted
import java.util.*

abstract class LogicalExpression protected constructor(val type: String): WhenCaseMatchExpression
interface ComparisonOperand: WhenCaseMatchExpression
data class OrExpression(val left: LogicalExpression, val right: LogicalExpression): LogicalExpression("or") {
   override fun asTaxi(): String {
      return "${left.asTaxi()} || ${right.asTaxi()}"
   }
}

data class AndExpression(val left: LogicalExpression, val right: LogicalExpression): LogicalExpression("and") {
   override fun asTaxi(): String {
      return "${left.asTaxi()} && ${right.asTaxi()}"
   }
}

data class ComparisonExpression(val operator: ComparisonOperator, val left: ComparisonOperand, val right: ComparisonOperand): LogicalExpression("comp") {
   override fun asTaxi(): String {
      return "${left.asTaxi()} ${operator.symbol} ${right.asTaxi()}"
   }
}

data class LogicalConstant(val value: Boolean): LogicalExpression("const") {
   override fun asTaxi(): String {
      return if (value) {
         "TRUE".quoted()
      } else {
         "FALSE".quoted()
      }
   }
}

data class LogicalVariable(val variableName: String): LogicalExpression("var") {
   override fun asTaxi(): String {
      return "this.$variableName"
   }
}

abstract class ComparisonOperandEntity protected constructor(val type: String): ComparisonOperand

data class ConstantEntity(val value: Any?): ComparisonOperandEntity("constAny") {
   override fun asTaxi(): String {
      return when (value) {
         is String -> value.quoted()
         null -> "null"
         else -> value.toString()
      }
   }
}

data class FieldReferenceEntity(val fieldName: String): ComparisonOperandEntity("var") {
   override fun asTaxi(): String {
      return "this.$fieldName"
   }
}

enum class ComparisonOperator(val symbol: String, val applicablePrimitives: EnumSet<PrimitiveType>) {
   GT(">", EnumSet.of(PrimitiveType.INTEGER, PrimitiveType.DECIMAL, PrimitiveType.DOUBLE)),
   GE (">=", EnumSet.of(PrimitiveType.INTEGER, PrimitiveType.DECIMAL, PrimitiveType.DOUBLE)),
   LT ("<", EnumSet.of(PrimitiveType.INTEGER, PrimitiveType.DECIMAL, PrimitiveType.DOUBLE)),
   LE ("<=", EnumSet.of(PrimitiveType.INTEGER, PrimitiveType.DECIMAL, PrimitiveType.DOUBLE)),
   EQ ("=", EnumSet.of(PrimitiveType.INTEGER, PrimitiveType.DECIMAL, PrimitiveType.DOUBLE, PrimitiveType.STRING)),
   NQ ("!=", EnumSet.of(PrimitiveType.INTEGER, PrimitiveType.DECIMAL, PrimitiveType.DOUBLE, PrimitiveType.STRING));
   companion object {
      private val bySymbol = ComparisonOperator.values().associateBy { it.symbol }
      fun forSymbol(symbol: String): ComparisonOperator {
         return bySymbol[symbol] ?: error("No operator defined for symbol $symbol")
      }
   }
}





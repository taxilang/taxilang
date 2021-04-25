package lang.taxi.types

import lang.taxi.utils.quoted
import java.util.*

// TODO : Using PrimtiiveType.ANY to disable type checking on expressions, because we haven't implemented it yet.
abstract class LogicalExpression protected constructor(val keyword: String, override val type:Type = PrimitiveType.ANY): WhenCaseMatchExpression
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

// TODO : Using Primtitive.Any as the type here to disable type checking, because we haven't
// implemented it yet
abstract class ComparisonOperandEntity protected constructor(val keyword: String, override val type: Type = PrimitiveType.ANY): ComparisonOperand

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

data class ModelAttributeFieldReferenceEntity(val source: QualifiedName, val fieldType: Type): ComparisonOperandEntity("") {
   override fun asTaxi(): String {
      return  "${source}.${fieldType.qualifiedName}"
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





package lang.taxi

import lang.taxi.accessors.NullValue
import org.antlr.v4.runtime.tree.TerminalNode
import java.math.BigDecimal

// Use in scenarios where null is permitted.  It's preferrable not to allow null,
// and to vall value()
fun TaxiParser.LiteralContext.valueOrNull(): Any? {
   return if (this.isNullValue()) null else this.value()
}

// Use in scnearios where null as a concept is permitted (such as value assignment),
// but we want to avoid null references.
// Returns NullValue or the underlying value
fun TaxiParser.LiteralContext.valueOrNullValue():Any {
   return if (this.isNullValue()) NullValue else this.value()
}
fun TaxiParser.LiteralContext.nullableValue(): Any? {
   return when {
      this.isNullValue() -> null
      else -> value()
   }
}
fun TaxiParser.LiteralContext.value(): Any {
   return when {
      this.BooleanLiteral() != null -> this.BooleanLiteral().text.toBoolean()
      this.StringLiteral() != null -> stringLiteralValue(this.StringLiteral())
      this.IntegerLiteral() != null -> numericLiteralValue(this.IntegerLiteral())
      this.DecimalLiteral() != null -> BigDecimal(this.DecimalLiteral().text)
      this.isNullValue() -> error("null is not permitted here")
      else -> TODO()
//      this.IntegerLiteral() != null -> this.IntegerLiteral()
   }
}

fun numericLiteralValue(literal: TerminalNode): Any {
   val longValue = literal.text.toLong()
   return if (longValue in Int.MIN_VALUE..Int.MAX_VALUE) {
      longValue.toInt()
   } else {
      longValue
   }
}
fun stringLiteralValue(stringLiteral: TerminalNode): String {
   val raw: String = stringLiteral.text
   return when {
      // Triple quotes (multi-line strings
      raw.startsWith(TRIPLE_QUOTE) && raw.endsWith(TRIPLE_QUOTE) -> raw.removeSurrounding(TRIPLE_QUOTE)
      // Otherwise remove either the " or '
      else -> stringLiteral.text.removeSurrounding(stringLiteral.text.substring(0, 1))
   }
}

private const val TRIPLE_QUOTE = "\"\"\""

//fun TaxiParser.InstantOffsetExpressionContext?.intValue(): Int? {
//   return when {
//      this == null  -> null
//      this.IntegerLiteral() != null -> this.IntegerLiteral().text.toInt()
//      else -> null
//   }
//}

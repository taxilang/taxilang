package lang.taxi

import org.antlr.v4.runtime.tree.TerminalNode
import java.math.BigDecimal

// Use in scenarios where null is permitted.  It's preferrable not to allow null,
// and to vall value()
fun TaxiParser.LiteralContext.valueOrNull(): Any? {
   return if (this.isNullValue()) null else this.value()
}

fun TaxiParser.LiteralContext.value(): Any {
   return when {
      this.BooleanLiteral() != null -> this.BooleanLiteral().text.toBoolean()
      this.StringLiteral() != null -> stringLiteralValue(this.StringLiteral())
      this.IntegerLiteral() != null -> this.IntegerLiteral().text.toInt()
      this.DecimalLiteral() != null -> BigDecimal(this.DecimalLiteral().text)
      this.isNullValue() -> error("Null is not permitted here")
      else -> TODO()
//      this.IntegerLiteral() != null -> this.IntegerLiteral()
   }
}

fun stringLiteralValue(stringLiteral: TerminalNode): String {
   return stringLiteral.text.removeSurrounding(stringLiteral.text.substring(0, 1))
}

fun TaxiParser.LiteralArrayContext.value(): List<Any> {
   return this.literal().map { it.value() }
}

fun TaxiParser.InstantOffsetExpressionContext?.intValue(): Int? {
   return when {
      this == null  -> null
      this.IntegerLiteral() != null -> this.IntegerLiteral().text.toInt()
      else -> null
   }
}

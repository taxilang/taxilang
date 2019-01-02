package lang.taxi

// Use in scenarios where null is permitted.  It's preferrable not to allow null,
// and to vall value()
fun TaxiParser.LiteralContext.valueOrNull(): Any? {
    return if (this.isNullValue()) null else this.value()
}

fun TaxiParser.LiteralContext.value(): Any {
    return when {
        this.StringLiteral() != null -> {
            // can be either ' or "
            val firstChar = this.StringLiteral().text.toCharArray()[0]
            this.StringLiteral().text.trim(firstChar)
        }
        this.IntegerLiteral() != null -> this.IntegerLiteral().text.toInt()
        this.BooleanLiteral() != null -> this.BooleanLiteral().text.toBoolean()
        this.isNullValue() -> error("Null is not permitted here")
        else -> TODO()
//      this.IntegerLiteral() != null -> this.IntegerLiteral()
    }
}

fun TaxiParser.LiteralArrayContext.value(): List<Any> {
    return this.literal().map { it.value() }
}
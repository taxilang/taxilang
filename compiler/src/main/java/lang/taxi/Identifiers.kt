package lang.taxi

fun List<TaxiParser.IdentifierContext>.text(): String {
   return this.joinToString(".") { it.text }
}

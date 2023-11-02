package lang.taxi

import lang.taxi.TaxiParser.DocumentContext

/**
 * A TaxiParser which provides the ability to mutate a token stream
 * and recover from compilation errors.
 *
 * Generally, this is useful in scenarios where we care more about the
 * structure of a document, than the contents itself (eg.: AutoComplete tooling).
 *
 * The ANTLR design of the underlying taxi parser makes it difficult to implement
 * this transparently (document is final), so a ParserCustomizer can return a RecoveringTaxiParser
 * instead
 */
interface RecoveringTaxiParser {
   val recoveredDocument : DocumentContext?

   val originalOrRecoveredDocument : DocumentContext
}

interface ParserCustomizer {
   fun configure(parser: TaxiTokenStreamParser):TaxiTokenStreamParser
}

package lang.taxi.lsp.parser

import lang.taxi.DefaultTaxiTokenStreamParser
import lang.taxi.ParserCustomizer
import lang.taxi.TaxiParser
import lang.taxi.TaxiTokenStreamParser
import lang.taxi.TokenStreamParseResult
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CommonToken
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.DefaultErrorStrategy
import org.antlr.v4.runtime.Parser
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.TokenStream

/**
 * An error strategy which attempts to recover by injecting
 * bogus tokens.
 *
 * This is intended for UI tooling (such as the LSP),
 * where we want to try to build a document, even when
 * there are sytnax errors, so that we can continue
 * to offer completions etc.
 *
 * The resulting document taht
 */
class TokenInjectingErrorStrategy(private val recoveryParser: RecoveringTokenStreamParser) : DefaultErrorStrategy() {


   companion object {
      val parserCustomizer: ParserCustomizer = object : ParserCustomizer {
         override fun configure(parser: TaxiTokenStreamParser): TaxiTokenStreamParser {
            return RecoveringTokenStreamParser(parser)
         }
      }
   }

   override fun recover(recognizer: Parser, e: RecognitionException) {
      val taxiParser = recognizer as TaxiParser
      val tokenStream = taxiParser.tokenStream as CommonTokenStream
      val offendingToken = e.offendingToken

      // TODO : Get the correct type
      val fakeToken = CommonToken(74, "FAKE")


      fakeToken.line = offendingToken.line
      fakeToken.charPositionInLine = offendingToken.charPositionInLine - 1
      tokenStream.tokens.add(offendingToken.tokenIndex, fakeToken)

      tokenStream.seek(0)
      recoveryParser.doRecoveryParse(tokenStream, fakeToken)

      super.recover(recognizer, e)
   }
}

/**
 * A Token Stream Parser which, in the event of a compilation error,
 * will inject ficticious tokens into the token stream to try to recover.
 *
 * This is useful for IDE tooling, where we want to try and compile, even
 * if there are syntax errors (which, while the user is typing, is almost all the time).
 */
class RecoveringTokenStreamParser(private val parser: TaxiTokenStreamParser) : DefaultTaxiTokenStreamParser() {

   private val tokenInjector = TokenInjectingErrorStrategy(this)

   /**
    * The result from parsing the modified tokens, when recovering from an error.
    */
   private var recoveryParseResult: TokenStreamParseResult? = null
   private var isInRecoveryMode: Boolean = false

   override fun buildParser(tokenStream: TokenStream): TaxiParser {
      val parser = super.buildParser(tokenStream)
      if (!isInRecoveryMode) {
         parser.errorHandler = tokenInjector
      }
      return parser
   }

   fun doRecoveryParse(stream: TokenStream, fakeToken: CommonToken) {
      isInRecoveryMode = true
      recoveryParseResult = parse(stream, "ErrorRecovery")
         .copy(syntheticTokens = listOf(fakeToken))
   }

   override fun parse(input: CharStream): TokenStreamParseResult {
      val result = super.parse(input)
      return if (isInRecoveryMode) {
         recoveryParseResult!!
      } else {
         result
      }
   }


}

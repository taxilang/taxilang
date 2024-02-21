package lang.taxi

import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ConsoleErrorListener
import org.antlr.v4.runtime.TokenStream

interface TaxiTokenStreamParser {

   fun parse(input: CharStream): TokenStreamParseResult
}


open class DefaultTaxiTokenStreamParser : TaxiTokenStreamParser {
   override fun parse(input: CharStream): TokenStreamParseResult {
      val lexer = TaxiLexer(input)
      // We ignore lexer errors, and let the parser handle syntax problems.
      // Without this, there's just a bunch of noise in the console.
      lexer.removeErrorListeners()

      val commonTokenStream = CommonTokenStream(lexer)
      return parse(commonTokenStream, input.sourceName)
   }

   protected open fun buildParser(tokenStream: TokenStream): TaxiParser {
      return TaxiParser(tokenStream)
   }

   fun parse(tokenStream: TokenStream, sourceName: String): TokenStreamParseResult {

      val parser = buildParser(tokenStream)
      parser.removeErrorListeners()

      val listener = TokenCollator()
      val errorListener = CollectingErrorListener(sourceName, listener)
      parser.addParseListener(listener)
      parser.addErrorListener(errorListener)
      // Use CompilerExceptions for runtime exceptions thrown by the compiler
      // (rather than problems in the source code being compiled)
      // These exceptions represent bugs in the compiler
      val compilerExceptions = mutableListOf<CompilationError>()

      try {
         // Calling document triggers the parsing
         parser.document()
      } catch (e: Exception) {
         compilerExceptions.add(
            CompilationError(
               parser.currentToken,
               "An exception occurred in the compilation process.  This is likely a bug in the Taxi Compiler. \n ${e.message}",
               parser.currentToken?.tokenSource?.sourceName
                  ?: "Unknown"
            )
         )
      }

      val result = TokenStreamParseResult(listener.tokens(), compilerExceptions + errorListener.errors)
      return result
   }

}

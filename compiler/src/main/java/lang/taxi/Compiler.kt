package lang.taxi

import lang.taxi.compiler.TokenProcessor
import lang.taxi.types.*
import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.misc.Interval
import java.io.File
import java.util.*

object Namespaces {
   const val DEFAULT_NAMESPACE = ""
}

fun ParserRuleContext?.toCompilationUnit(): lang.taxi.types.CompilationUnit {
   return if (this == null) {
      CompilationUnit.unspecified()
   } else {
      lang.taxi.types.CompilationUnit(this, this.source());
   }

}

data class CompilationError(val offendingToken: Token, val detailMessage: String?, val sourceName: String? = null) {
   val line = offendingToken.line
   val char = offendingToken.charPositionInLine

   override fun toString(): String = "Compilation Error: ${sourceName.orEmpty()}($line,$char) $detailMessage"
}

class CompilationException(val errors: List<CompilationError>) : RuntimeException(errors.joinToString { it.toString() }) {
   constructor(offendingToken: Token, detailMessage: String?, sourceName: String?) : this(listOf(CompilationError(offendingToken, detailMessage, sourceName)))
}

data class DocumentStrucutreError(val detailMessage: String)
class DocumentMalformedException(val errors: List<DocumentStrucutreError>) : RuntimeException(errors.joinToString { it.detailMessage })
class Compiler(val inputs: List<CharStream>, val importSources: List<TaxiDocument> = emptyList()) {
   constructor(input: CharStream, importSources: List<TaxiDocument> = emptyList()) : this(listOf(input), importSources)
   constructor(source: String, sourceName: String = "[unknown source]", importSources: List<TaxiDocument> = emptyList()) : this(CharStreams.fromString(source, sourceName), importSources)
   constructor(file: File, importSources: List<TaxiDocument> = emptyList()) : this(CharStreams.fromPath(file.toPath()), importSources)

   companion object {
      fun forStrings(sources: List<String>) = Compiler(sources.mapIndexed { index, source -> CharStreams.fromString(source, "StringSource-$index") })
      fun forStrings(vararg source: String) = forStrings(source.toList())
   }

   private val tokens: Tokens by lazy {
      collectTokens()
   }

   fun validate(): List<CompilationError> {
      try {
         compile()
      } catch (e: CompilationException) {
         return e.errors
      }
      return emptyList()
   }

   /**
    * Returns a list of types declared in this file, including inline type aliases
    * The source is not validated to perform this task, so compilation errors (beyond grammatical errors) are not thrown,
    * and the file may not be valid source.
    */
   fun declaredTypeNames(): List<QualifiedName> {
      val tokenProcessor = TokenProcessor(tokens, collectImports = false)
      return tokenProcessor.findDeclaredTypeNames()
   }

   /**
    * Returns a list of imports declared in this file.
    * The source is not validated to perform this task, so compilation errors (beyond grammatical errors) are not thrown,
    * and the file may not be valid source.
    */
   fun declaredImports(): List<QualifiedName> {
      return tokens.imports.map { (name, _) -> QualifiedName.from(name) }
   }

   fun compile(): TaxiDocument {
      val builder = TokenProcessor(tokens, importSources)
      return builder.buildTaxiDocument()
   }

   /**
    * Note that indexes are 1-Based, not 0-Based
    */
   fun contextAt(line: Int, char: Int): ParserRuleContext? {
      val row = tokens.tokenTable.row(line) as SortedMap
      if (row.isEmpty()) {
         return null
      }
      val tokenStartIndices = row.keys as SortedSet
      val nearestStartIndex = tokenStartIndices.takeWhile { startIndex -> startIndex <= char }.lastOrNull()
      return nearestStartIndex?.let { index -> row.get(index) }
   }

   private fun collectTokens(): Tokens {
      val tokensCollection = inputs.map { input ->
         val listener = TokenCollator()
         val errorListener = CollectingErrorListener(input.sourceName)
         val lexer = TaxiLexer(input)
         val parser = TaxiParser(CommonTokenStream(lexer))
         parser.addParseListener(listener)
         parser.addErrorListener(errorListener)
         val doc = parser.document()
         doc.exception?.let {
            throw CompilationException(it.offendingToken, it.message, input.sourceName)
         }
         if (errorListener.errors.isNotEmpty())
            throw CompilationException(errorListener.errors)

         listener.tokens() // return..
      }
      val tokens = tokensCollection.reduce { acc, tokens -> acc.plus(tokens) }
      return tokens
   }
}


internal fun ErrorMessage?.toCompilationError(start: Token): CompilationError? {
   if (this == null) {
      return null
   }
   return CompilationError(start, this)
}

internal fun TaxiParser.OperationSignatureContext.parameters(): List<TaxiParser.OperationParameterContext> {
   return this.operationParameterList()?.operationParameter() ?: emptyList()
}

fun ParserRuleContext.source(): SourceCode {
   val text = this.start.inputStream.getText(Interval(this.start.startIndex, this.stop.stopIndex))
   val origin = this.start.inputStream.sourceName
   return SourceCode(origin, text)
}

fun TaxiParser.LiteralContext.isNullValue(): Boolean {
   return this.text == "null"
}



typealias TypeResolver = (TaxiParser.TypeTypeContext) -> Type

package lang.taxi

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import lang.taxi.compiler.TokenProcessor
import lang.taxi.types.*
import lang.taxi.utils.log
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
class CompilerTokenCache {
   private val streamNameToStream = mutableMapOf<String, CharStream>()
   private val cache: Cache<CharStream, TokenStreamParseResult> = CacheBuilder.newBuilder()
      .build<CharStream, TokenStreamParseResult>()

   fun parse(input: CharStream): TokenStreamParseResult {
      return cache.get(input) {
         val listener = TokenCollator()
         val errorListener = CollectingErrorListener(input.sourceName)
         val lexer = TaxiLexer(input)
         val parser = TaxiParser(CommonTokenStream(lexer))
         parser.addParseListener(listener)
         parser.addErrorListener(errorListener)

         // Calling document triggers the parsing
         parser.document()
         val result = TokenStreamParseResult(listener.tokens(), errorListener.errors)

         streamNameToStream.put(SourceNames.normalize(input.sourceName), input)?.let { previousVersion ->
            cache.invalidate(previousVersion)
         }
         result
      }
   }
}

data class TokenStreamParseResult(val tokens: Tokens, val errors: List<CompilationError>)
class Compiler(val inputs: List<CharStream>, val importSources: List<TaxiDocument> = emptyList(), private val tokenCache: CompilerTokenCache = CompilerTokenCache()) {
   constructor(input: CharStream, importSources: List<TaxiDocument> = emptyList()) : this(listOf(input), importSources)
   constructor(source: String, sourceName: String = UNKNOWN_SOURCE, importSources: List<TaxiDocument> = emptyList()) : this(CharStreams.fromString(source, sourceName), importSources)
   constructor(file: File, importSources: List<TaxiDocument> = emptyList()) : this(CharStreams.fromPath(file.toPath()), importSources)

   companion object {
      const val UNKNOWN_SOURCE = "UnknownSource"
      fun forStrings(sources: List<String>) = Compiler(sources.mapIndexed { index, source -> CharStreams.fromString(source, "StringSource-$index") })
      fun forStrings(vararg source: String) = forStrings(source.toList())
   }

   private val parseResult: Pair<Tokens, List<CompilationError>> by lazy {
      collectTokens()
   }

   private val tokens: Tokens by lazy {
      parseResult.first
   }

   private val syntaxErrors: List<CompilationError> by lazy {
      parseResult.second
   }

   fun validate(): List<CompilationError> {
      val compilationErrors = parseResult.second
      if (compilationErrors.isNotEmpty()) {
         return compilationErrors
      }
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
      // Note - leaving this approach for backwards compatiability
      // We could try to continue compiling, with the tokens we do have
      if (syntaxErrors.isNotEmpty()) {
         throw CompilationException(syntaxErrors)
      }
      val builder = TokenProcessor(tokens, importSources)
      return builder.buildTaxiDocument()
   }

   /**
    * Note that indexes are 1-Based, not 0-Based
    */
   fun contextAt(line: Int, char: Int, sourceName: String = UNKNOWN_SOURCE): ParserRuleContext? {
      val tokenTable = tokens.tokenStore.tokenTable(sourceName)
      val row = tokenTable.row(line) as SortedMap
      if (row.isEmpty()) {
         return null
      }
      val tokenStartIndices = row.keys as SortedSet
      val nearestStartIndex = tokenStartIndices.takeWhile { startIndex -> startIndex <= char }.lastOrNull()
      return nearestStartIndex?.let { index -> row.get(index) }
   }

   /**
    * Collect the tokens in the input streams found
    * Here, errors will get thrown for syntax issues, but not for
    * semantic issues (eg., invalid types etc).
    *
    * We try to collect as many tokens as possible, to have the richest
    * view of the source.  So, if one stream fails, we'll exclude it and try
    * to parse the rest of the streams.
    * This is to allow tooling (such as VSCode / LSP) to get as much token / type data
    * as possible
    */
   private fun collectTokens(): Pair<Tokens, List<CompilationError>> {

      val collectionResult = inputs.map { input ->
         // We cache the result.
         // This is primarily because the input is a stream, and once parsed the first
         // time, we have to seek back to the start to reparse.
         // This seems wasteful, so we cache.
         // There are other benefits in terms of performance, but these are currently a secondary concern
         tokenCache.parse(input)
      }
      val tokensCollection = collectionResult.map { it.tokens }
      val errors = collectionResult.flatMap { it.errors }
      val tokens = tokensCollection.reduce { acc, tokens -> acc.plus(tokens) }
      return tokens to errors
   }

   fun typeNamesForSource(sourceName: String): List<QualifiedName> {
      return tokens.typeNamesForSource(sourceName)
   }

   fun importedTypesInSource(sourceName: String): List<QualifiedName> {
      return tokens.importedTypeNamesInSource(sourceName)
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

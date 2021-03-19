package lang.taxi

import arrow.core.Either
import arrow.core.getOrHandle
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import lang.taxi.compiler.TokenProcessor
import lang.taxi.compiler.TypeChecker
import lang.taxi.functions.stdlib.StdLib
import lang.taxi.linter.Linter
import lang.taxi.linter.LinterRuleConfiguration
import lang.taxi.linter.toLinterRules
import lang.taxi.messages.Severity
import lang.taxi.packages.TaxiPackageSources
import lang.taxi.sources.SourceCode
import lang.taxi.sources.SourceLocation
import lang.taxi.toggles.FeatureToggle
import lang.taxi.types.*
import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.misc.Interval
import java.io.File
import java.io.Serializable
import java.util.*

object Namespaces {
   const val DEFAULT_NAMESPACE = ""
}

fun ParserRuleContext?.toCompilationUnit(): lang.taxi.types.CompilationUnit {
   return if (this == null) {
      CompilationUnit.unspecified()
   } else {
      lang.taxi.types.CompilationUnit(
         this,
         this.source(),
         SourceLocation(this.start.line, this.start.charPositionInLine)
      );
   }
}

fun ParserRuleContext?.toCompilationUnits(): List<CompilationUnit> {
   return listOf(this.toCompilationUnit())
}

typealias CompilationMessage = CompilationError

data class CompilationError(
   val line: Int,
   val char: Int,
   val detailMessage: String,
   val sourceName: String? = null,
   val severity: Severity = Severity.ERROR
) : Serializable {
   constructor(
      compiled: Compiled,
      detailMessage: String,
      sourceName: String = compiled.compilationUnits.first().source.sourceName,
      severity: Severity = Severity.ERROR
   ) : this(
      compiled.compilationUnits.firstOrNull()?.location
         ?: SourceLocation.UNKNOWN_POSITION, detailMessage, sourceName, severity
   )

   constructor(
      compilationUnit: CompilationUnit,
      detailMessage: String,
      sourceName: String = compilationUnit.source.sourceName,
      severity: Severity = Severity.ERROR
   ) : this(
      compilationUnit.location, detailMessage, sourceName, severity
   )

   constructor(
      position: SourceLocation,
      detailMessage: String,
      sourceName: String? = null,
      severity: Severity = Severity.ERROR
   ) : this(position.line, position.char, detailMessage, sourceName, severity)

   constructor(
      offendingToken: Token,
      detailMessage: String,
      sourceName: String = offendingToken.tokenSource.sourceName,
      severity: Severity = Severity.ERROR
   ) : this(offendingToken.line, offendingToken.charPositionInLine, detailMessage, sourceName, severity)


   override fun toString(): String = "[${severity.label}]: ${sourceName.orEmpty()}($line,$char) $detailMessage"
}

open class CompilationException(val errors: List<CompilationError>) :
   RuntimeException(errors.joinToString("\n") { it.toString() }) {
   constructor(error: CompilationError) : this(listOf(error))
   constructor(offendingToken: Token, detailMessage: String, sourceName: String) : this(
      listOf(
         CompilationError(
            offendingToken,
            detailMessage,
            sourceName
         )
      )
   )
}

data class DocumentStrucutreError(val detailMessage: String)
class DocumentMalformedException(val errors: List<DocumentStrucutreError>) :
   RuntimeException(errors.joinToString { it.detailMessage })

class CompilerTokenCache {
   private val streamNameToStream = mutableMapOf<String, CharStream>()
   private val cache: Cache<CharStream, TokenStreamParseResult> = CacheBuilder.newBuilder()
      .build<CharStream, TokenStreamParseResult>()

   fun parse(input: CharStream): TokenStreamParseResult {
      return cache.get(input) {
         val listener = TokenCollator()
         val errorListener = CollectingErrorListener(input.sourceName)
         val lexer = TaxiLexer(input)
         // We ignore lexer errors, and let the parser handle syntax problems.
         // Without this, there's just a bunch of noise in the console.
         lexer.removeErrorListeners()

         val parser = TaxiParser(CommonTokenStream(lexer))
         parser.addParseListener(listener)
         parser.addErrorListener(errorListener)

         // Use CompilerExceptions for runtime exceptions thrown by the compiler
         // not compilatio errors in the source code being compiled
         // These exceptions represent bugs in the compiler
         val compilerExceptions = mutableListOf<CompilationError>()
         // Calling document triggers the parsing
         try {
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

         streamNameToStream.put(SourceNames.normalize(input.sourceName), input)?.let { previousVersion ->
            cache.invalidate(previousVersion)
         }
         result
      }
   }
}

data class TokenStreamParseResult(val tokens: Tokens, val errors: List<CompilationError>)
data class CompilerConfig(
   val typeCheckerEnabled: FeatureToggle = FeatureToggle.DISABLED,
   val linterRuleConfiguration: List<LinterRuleConfiguration> = emptyList()
) {
   val linter: Linter by lazy { Linter(linterRuleConfiguration) }

   companion object {

   }
}

class Compiler(
   val inputs: List<CharStream>,
   val importSources: List<TaxiDocument> = emptyList(),
   private val tokenCache: CompilerTokenCache = CompilerTokenCache(),
   val config: CompilerConfig = CompilerConfig()
) {
   constructor(
      input: CharStream,
      importSources: List<TaxiDocument> = emptyList(),
      config: CompilerConfig = CompilerConfig()
   ) : this(listOf(input), importSources, config = config)

   constructor(
      source: String,
      sourceName: String = UNKNOWN_SOURCE,
      importSources: List<TaxiDocument> = emptyList(),
      config: CompilerConfig = CompilerConfig()
   ) : this(CharStreams.fromString(source, sourceName), importSources, config = config)

   constructor(
      file: File,
      importSources: List<TaxiDocument> = emptyList(),
      config: CompilerConfig = CompilerConfig()
   ) : this(CharStreams.fromPath(file.toPath()), importSources, config = config)

   constructor(
      project: TaxiPackageSources,
      config: CompilerConfig = CompilerConfig(linterRuleConfiguration = project.project.linter.toLinterRules())
   ) : this(project.sources.map { CharStreams.fromString(it.content, it.sourceName) }, config = config)

   companion object {
      const val UNKNOWN_SOURCE = "UnknownSource"
      fun forStrings(sources: List<String>) =
         Compiler(sources.mapIndexed { index, source -> CharStreams.fromString(source, "StringSource-$index") })

      fun forFiles(sources: List<File>) =
         Compiler(sources.mapIndexed { index, source -> CharStreams.fromPath(source.toPath()) })

      fun forStrings(vararg source: String) = forStrings(source.toList())
   }

   private val typeChecker: TypeChecker = TypeChecker(config.typeCheckerEnabled)

   private val parseResult: Pair<Tokens, List<CompilationError>> by lazy {
      collectTokens()
   }

   private val tokens: Tokens by lazy {
      parseResult.first
   }

   private val syntaxErrors: List<CompilationError> by lazy {
      parseResult.second
   }
   private val tokenProcessrWithImports: TokenProcessor by lazy {
      TokenProcessor(tokens, importSources, typeChecker = typeChecker, linter = config.linter)
   }
   private val tokenprocessorWithoutImports: TokenProcessor by lazy {
      TokenProcessor(tokens, collectImports = false, typeChecker = typeChecker, linter = config.linter)
   }

   fun validate(): List<CompilationError> {
      val compilationErrors = parseResult.second
      if (compilationErrors.isNotEmpty()) {
         return compilationErrors
      }
      try {
         val (messages, _) = compileWithMessages()
         return messages
      } catch (e: CompilationException) {
         return e.errors
      }
   }

   /**
    * Returns a list of types declared in this file, including inline type aliases
    * The source is not validated to perform this task, so compilation errors (beyond grammatical errors) are not thrown,
    * and the file may not be valid source.
    */
   fun declaredTypeNames(): List<QualifiedName> {
      return tokenprocessorWithoutImports.findDeclaredTypeNames()
   }

   fun lookupTypeByName(typeType: TaxiParser.TypeTypeContext): QualifiedName {
      return QualifiedName.from(tokenProcessrWithImports.lookupTypeByName(typeType))
   }

   fun getDeclarationSource(text: String, context: ParserRuleContext): CompilationUnit? {
      val qualifiedName = tokenProcessrWithImports.lookupTypeByName(text, context)
         .getOrHandle { errors -> throw CompilationException(errors) }
      return getCompilationUnit(tokenProcessrWithImports, qualifiedName)
   }

   fun getDeclarationSource(name: QualifiedName): CompilationUnit? {
      return getCompilationUnit(tokenProcessrWithImports, name.fullyQualifiedName)
   }

   private fun getCompilationUnit(processor: TokenProcessor, qualifiedName: String): CompilationUnit? {
      val definition = processor.findDefinition(qualifiedName) ?: return null

      // don't return the start of the documentation, as that's not really what
      // was intended
      val startOfDeclaration =
         if (definition.children.any { it::class.java == TaxiParser.TypeDocContext::class.java }) {
            definition.children.filter { it::class.java != TaxiParser.TypeDocContext::class.java }
               .filterIsInstance<ParserRuleContext>()
               .firstOrNull() ?: definition

         } else {
            definition
         }
      return startOfDeclaration.toCompilationUnit()
   }

   fun getDeclarationSource(typeName: TaxiParser.TypeTypeContext): CompilationUnit? {
      val qualifiedName = tokenProcessrWithImports.lookupTypeByName(typeName)
      return getCompilationUnit(tokenProcessrWithImports, qualifiedName)
   }

   /**
    * Returns a list of imports declared in this file.
    * The source is not validated to perform this task, so compilation errors (beyond grammatical errors) are not thrown,
    * and the file may not be valid source.
    */
   fun declaredImports(): List<QualifiedName> {
      return tokens.imports.map { (name, _) -> QualifiedName.from(name) }
   }

   fun compileWithMessages(): Pair<List<CompilationError>, TaxiDocument> {
      // Note - leaving this approach for backwards compatiability
      // We could try to continue compiling, with the tokens we do have
      if (syntaxErrors.isNotEmpty()) {
         return syntaxErrors to TaxiDocument.empty()
      }
      val builder = tokenProcessrWithImports
      // Similarly to above, we could do somethign with these errors now.
      val (errors, document) = builder.buildTaxiDocument()
      return errors to document
   }

   fun compile(): TaxiDocument {
      val (messages, document) = compileWithMessages()
      val errors = messages.filter { it.severity == Severity.ERROR }
      if (errors.isNotEmpty()) {
         throw CompilationException(errors)
      }
      return document
   }

   /**
    * Note that indexes are 0-Based
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
      val builtInSources = CharStreams.fromString(StdLib.taxi, "Native StdLib")

      val allInputs = inputs + builtInSources
      val collectionResult = allInputs.map { input ->
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

   fun importTokensInSource(sourceName: String): List<Pair<QualifiedName, TaxiParser.ImportDeclarationContext>> {
      return tokens.importTokensInSource(sourceName)
   }

   fun importedTypesInSource(sourceName: String): List<QualifiedName> {
      return tokens.importedTypeNamesInSource(sourceName)
   }

   fun usedTypedNamesInSource(sourceName: String): Set<QualifiedName> {
      return tokenProcessrWithImports.tokens.tokenStore.getTypeReferencesForSourceName(sourceName).mapNotNull {
         try {
            tokenProcessrWithImports.lookupTypeByName(it)
         } catch (error: CompilationException) {
            null
         }
      }.map { QualifiedName.from(it) }
         .toSet()
   }
}

internal fun <B> Either<ErrorMessage, B>.toCompilationError(start: Token): Either<CompilationError, B> {
   return this.mapLeft { errorMessage -> errorMessage.toCompilationError(start)!! }
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


interface NamespaceQualifiedTypeResolver {
   val namespace: String
   fun resolve(context: TaxiParser.TypeTypeContext): Either<List<CompilationError>, Type>

   /**
    * Resolves a type name that has been requested in a source file.
    * Considers the namespace it's declared in, and declared imports to qualify
    * short-hand references to fully-qualified imports
    */
   fun resolve(requestedTypeName: String, context: ParserRuleContext): Either<List<CompilationError>, Type>
}


// A chicken out method, where I can't be bothered
// wrapping Either<> call sites to handle the error.
// Just throw the exception.
// In future, we'll be better, promise.
fun Either<List<CompilationError>, Type>.orThrowCompilationException(): Type {
   return this.getOrHandle { e -> throw CompilationException(e) }
}

fun RuleContext.importsInFile(): List<QualifiedName> {
   val topLevel = this.searchUpForRule(
      listOf(
         TaxiParser.SingleNamespaceDocumentContext::class.java,
         TaxiParser.MultiNamespaceDocumentContext::class.java
      )
   )
   if (topLevel == null || topLevel !is ParserRuleContext) {
      return emptyList()
   }
   val imports = topLevel.children.filterIsInstance<TaxiParser.ImportDeclarationContext>()
      .map { QualifiedName.from(it.qualifiedName().Identifier().text()) }
   return imports
}

tailrec fun RuleContext.searchUpForRule(ruleTypes: List<Class<out RuleContext>>): RuleContext? {
   fun matches(instance: RuleContext): Boolean {
      return ruleTypes.any { ruleType -> instance::class.java == ruleType }
   }

   if (matches(this)) {
      return this
   }

   val matchingChild = (0..this.childCount).mapNotNull { childIndex ->
      val child = this.getChild(childIndex)
      if (child is RuleContext && matches(child)) {
         child
      } else {
         null
      }
   }.firstOrNull()
   if (matchingChild != null) {
      return matchingChild
   }

   if (this.parent == null) {
      return null
   }

   return this.parent.searchUpForRule(ruleTypes)

}

fun RuleContext.findNamespace(): String {
   val namespaceRule = this.searchUpForRule(
      listOf(
         TaxiParser.NamespaceDeclarationContext::class.java,
         TaxiParser.NamespaceBlockContext::class.java,
         TaxiParser.SingleNamespaceDocumentContext::class.java
      )
   )
   return when (namespaceRule) {
      is TaxiParser.NamespaceDeclarationContext -> return namespaceRule.qualifiedName().Identifier().text()
      is TaxiParser.NamespaceBlockContext -> return namespaceRule.children.filterIsInstance<TaxiParser.QualifiedNameContext>()
         .first().Identifier().text()
      is TaxiParser.SingleNamespaceDocumentContext -> namespaceRule.children.filterIsInstance<TaxiParser.NamespaceDeclarationContext>()
         .firstOrNull()?.qualifiedName()?.Identifier()?.text()
         ?: Namespaces.DEFAULT_NAMESPACE
      else -> Namespaces.DEFAULT_NAMESPACE
   }
}

fun TaxiParser.NamespaceDeclarationContext.namespace(): String {
   return this.qualifiedName().Identifier().text()
}

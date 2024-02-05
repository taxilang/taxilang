package lang.taxi

import arrow.core.Either
import arrow.core.getOrHandle
import com.google.common.base.Stopwatch
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.collect.Table
import lang.taxi.compiler.TokenProcessor
import lang.taxi.functions.stdlib.StdLib
import lang.taxi.linter.Linter
import lang.taxi.linter.LinterRuleConfiguration
import lang.taxi.linter.toLinterRules
import lang.taxi.messages.Severity
import lang.taxi.packages.ImporterConfig
import lang.taxi.packages.TaxiPackageSources
import lang.taxi.packages.TaxiSourcesLoader
import lang.taxi.query.TaxiQlQuery
import lang.taxi.sources.SourceCode
import lang.taxi.sources.SourceLocation
import lang.taxi.toggles.FeatureToggle
import lang.taxi.types.*
import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.misc.Interval
import org.antlr.v4.runtime.tree.ParseTree
import org.taxilang.packagemanager.PackageManager
import java.io.File
import java.io.Serializable
import java.nio.file.Path
import java.util.*
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

object Namespaces {
   const val DEFAULT_NAMESPACE = ""
}

fun ParserRuleContext?.toCompilationUnit(
   /**
    * Creates a standalone source file, containing the source and namespaces for the
    * dependent types.
    */
   dependantTypeNames: List<QualifiedName> = emptyList(),

   /**
    * Looks in the source file declaring this context, and grabs all the imports
    */
   includeImportsPresentInFile: Boolean = false
): lang.taxi.types.CompilationUnit {
   return if (this == null) {
      CompilationUnit.unspecified()
   } else {
      val rawSource = this.source().let { src ->
         if (includeImportsPresentInFile) {
            val imports = this.importsInFile().joinToString("\n") { "import ${it.fullyQualifiedName}" }
            return@let src.copy(content = listOf(imports, src.content).joinToString("\n\n"))
         } else src
      }
      return CompilationUnit(
         rawSource.makeStandalone(this.findNamespace(), dependantTypeNames),
         SourceLocation(this.start.line, this.start.charPositionInLine)
      )
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
   val severity: Severity = Severity.ERROR,
   val errorCode: Int? = null,
) : Serializable {
   constructor(
      compiled: Compiled,
      detailMessage: String,
      sourceName: String = compiled.compilationUnits.first().source.sourceName,
      severity: Severity = Severity.ERROR,
      errorCode: Int? = null,
   ) : this(
      compiled.compilationUnits.firstOrNull()?.location
         ?: SourceLocation.UNKNOWN_POSITION, detailMessage, sourceName, severity, errorCode
   )

   constructor(
      compilationUnit: CompilationUnit,
      detailMessage: String,
      sourceName: String = compilationUnit.source.sourceName,
      severity: Severity = Severity.ERROR,
      errorCode: Int? = null,
   ) : this(
      compilationUnit.location, detailMessage, sourceName, severity, errorCode
   )

   constructor(
      position: SourceLocation,
      detailMessage: String,
      sourceName: String? = null,
      severity: Severity = Severity.ERROR,
      errorCode: Int? = null,
   ) : this(position.line, position.char, detailMessage, sourceName, severity, errorCode)

   constructor(
      offendingToken: Token,
      detailMessage: String,
      sourceName: String = offendingToken.tokenSource.sourceName,
      severity: Severity = Severity.ERROR,
      errorCode: Int? = null,
   ) : this(
      offendingToken.line,
      offendingToken.charPositionInLine,
      detailMessage,
      sourceName,
      severity,
      errorCode
   )


   override fun toString(): String = "[${severity.label}]: ${sourceName.orEmpty()}($line,$char) $detailMessage"
}

fun List<CompilationError>.errors(): List<CompilationError> = this.filter { it.severity == Severity.ERROR }

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


class CompilerTokenCache(
   private val parserCustomizers: List<ParserCustomizer> = emptyList()
) {

   private val streamNameToStream = mutableMapOf<String, CharStream>()
   private val cache: Cache<CharStream, TokenStreamParseResult> = CacheBuilder.newBuilder()
      .build<CharStream, TokenStreamParseResult>()

   fun get(sourceName: String): TokenStreamParseResult? {
      val normalizedSourceName = SourceNames.normalize(sourceName)
      val cacheMap = cache.asMap()
      return cacheMap.keys
         .firstOrNull { it.sourceName == normalizedSourceName }
         ?.let { cacheMap.get(it) }
   }

   fun parse(input: CharStream): TokenStreamParseResult {
      return cache.get(input) {
         val tokenStreamParser =
            parserCustomizers.fold(DefaultTaxiTokenStreamParser() as TaxiTokenStreamParser) { acc, parserCustomizer ->
               parserCustomizer.configure(acc)
            }

         tokenStreamParser.parse(input)

//         val listener = TokenCollator()
//         val errorListener = CollectingErrorListener(input.sourceName, listener)
//         val lexer = TaxiLexer(input)
//         // We ignore lexer errors, and let the parser handle syntax problems.
//         // Without this, there's just a bunch of noise in the console.
//         lexer.removeErrorListeners()
//
//         val commonTokenStream = CommonTokenStream(lexer)
//
//         // Extension point - allow callers to customize the parser
//         // (eg., adding in a custom error recovery strategy)
//         val parser = TaxiParser(commonTokenStream).let {parser ->
//            parser.addParseListener(listener)
//            parser.addErrorListener(errorListener)
//            parserCustomizers.fold(parser) { acc, parserCustomizer -> parserCustomizer.configure(acc) }
//         }
//
//         // Use CompilerExceptions for runtime exceptions thrown by the compiler
//         // not compilatio errors in the source code being compiled
//         // These exceptions represent bugs in the compiler
//         val compilerExceptions = mutableListOf<CompilationError>()
//
//         try {
//            // Calling document triggers the parsing
//            parser.document()
//         } catch (e: Exception) {
//            compilerExceptions.add(
//               CompilationError(
//                  parser.currentToken,
//                  "An exception occurred in the compilation process.  This is likely a bug in the Taxi Compiler. \n ${e.message}",
//                  parser.currentToken?.tokenSource?.sourceName
//                     ?: "Unknown"
//               )
//            )
//         }
//
//         val result = TokenStreamParseResult(listener.tokens(), compilerExceptions + errorListener.errors)
//
//         streamNameToStream.put(SourceNames.normalize(input.sourceName), input)?.let { previousVersion ->
//            cache.invalidate(previousVersion)
//         }
//         result
      }
   }
}

data class TokenStreamParseResult(
   val tokens: Tokens, val errors: List<CompilationError>,

   /**
    * Synthetic tokens are things that we created during the
    * compilation process, which weren't present in the original sources.
    *
    * Normally, this is through tooling for things like Autocomplete suggestions,
    * which will attempt to recover from compilation failures by injecting synthetic tokens
    */
   val syntheticTokens: List<Token> = emptyList()
)

data class CompilerConfig(
   val typeCheckerEnabled: FeatureToggle = FeatureToggle.ENABLED,
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
      sources: List<SourceCode>,
      importSources: List<TaxiDocument> = emptyList(),
      config: CompilerConfig = CompilerConfig()
   ) : this(sources.map { CharStreams.fromString(it.content, it.sourceName) }, importSources, config = config)

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

      fun forPackageWithDependencies(
         packageRootPath: Path,
         packageManager: PackageManager? = null
      ): Compiler {

         val thePackageManager = if (packageManager == null) {
            val rootProject = TaxiSourcesLoader.loadPackage(packageRootPath)
            PackageManager.withDefaultRepositorySystem(
               ImporterConfig.forProject(rootProject.project)
            )
         } else packageManager
         val sources = TaxiSourcesLoader.loadPackageAndDependencies(packageRootPath,thePackageManager)
         return Compiler(sources)
      }

   }

   private val typeChecker: TypeChecker = TypeChecker(config.typeCheckerEnabled)

   val parseResult: CollectedTokens by lazy {
      collectTokens()
   }

   val tokens: Tokens by lazy {
      parseResult.tokens
   }

   val syntheticTokens: List<Token> by lazy {
      parseResult.syntheticTokens
   }

   private val syntaxErrors: List<CompilationError> by lazy {
      parseResult.errors
   }
   private val tokenProcessorWithImports: TokenProcessor by lazy {
      TokenProcessor(tokens, importSources, typeChecker = typeChecker, linter = config.linter)
   }
   private val tokenProcessorWithoutImports: TokenProcessor by lazy {
      TokenProcessor(tokens, collectImports = false, typeChecker = typeChecker, linter = config.linter)
   }

   fun validate(): List<CompilationError> {
      val compilationErrors = parseResult.errors
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
      return tokenProcessorWithoutImports.findDeclaredTypeNames()
         .filterNot { BuiltIns.isBuiltIn(it) }
   }

   fun declaredServiceNames(): List<QualifiedName> {
      return tokenProcessorWithoutImports.findDeclaredServiceNames()
   }

   fun lookupSymbolByName(text: String, contextRule: ParserRuleContext): Either<List<CompilationError>, String> {
      return tokenProcessorWithImports.lookupSymbolByName(text, contextRule)
   }

   fun lookupTypeByName(typeType: TaxiParser.TypeReferenceContext): QualifiedName {
      return QualifiedName.from(tokenProcessorWithImports.lookupSymbolByName(typeType))
   }

   fun getDeclarationSource(text: String, context: ParserRuleContext): CompilationUnit? {
      val qualifiedName = tokenProcessorWithImports.lookupSymbolByName(text, context)
         .getOrHandle { errors -> throw CompilationException(errors) }
      return getCompilationUnit(tokenProcessorWithImports, qualifiedName)
   }

   fun getDeclarationSource(name: QualifiedName): CompilationUnit? {
      return getCompilationUnit(tokenProcessorWithImports, name.fullyQualifiedName)
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

   fun getDeclarationSource(typeName: TaxiParser.TypeReferenceContext): CompilationUnit? {
      val qualifiedName = tokenProcessorWithImports.lookupSymbolByName(typeName)
      return getCompilationUnit(tokenProcessorWithImports, qualifiedName)
   }

   /**
    * Returns a list of imports declared in this file.
    * The source is not validated to perform this task, so compilation errors (beyond grammatical errors) are not thrown,
    * and the file may not be valid source.
    */
   fun declaredImports(): List<QualifiedName> {
      return tokens.imports.map { (name, _) -> QualifiedName.from(name) }
   }

   fun queries(): List<TaxiQlQuery> {
      val (messages, queries) = queriesWithErrorMessages()
      val errors = messages.filter { it.severity == Severity.ERROR }
      if (errors.isNotEmpty()) {
         throw CompilationException(errors)
      }
      return queries
   }

   fun queriesWithErrorMessages(): Pair<List<CompilationError>, List<TaxiQlQuery>> {
      if (syntaxErrors.isNotEmpty()) {
         return syntaxErrors to listOf()
      }
      val builder = tokenProcessorWithImports
      val (errors, queries) = builder.buildQueries()
      return errors to queries
   }


   fun compileWithMessages(): Pair<List<CompilationError>, TaxiDocument> {
      val stopwatch = Stopwatch.createStarted()
      // Note - leaving this approach for backwards compatiability
      // We could try to continue compiling, with the tokens we do have
      if (syntaxErrors.isNotEmpty()) {
         return syntaxErrors to TaxiDocument.empty()
      }
      val builder = tokenProcessorWithImports
      // Similarly to above, we could do somethign with these errors now.
      val (errors, document) = builder.buildTaxiDocument()
//      log().debug("Taxi schema compilation took ${stopwatch.elapsed().toMillis()}ms")
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
   fun contextAt(zeroBasedLineIndex: Int, char: Int, sourceName: String = UNKNOWN_SOURCE): ParserRuleContext? {
      val sourceUri = SourceNames.normalize(sourceName)
      val tokenTable = tokens.tokenStore.tokenTable(sourceName)

      val closestLineWithContent = searchBackwardsForClosestLine(tokenTable, zeroBasedLineIndex) ?: return null
      val row = tokenTable.row(closestLineWithContent).let {
         if (it.isEmpty()) {
            // This is a workaround.  I've noticed that sometimes the value the compiler
            // contains in it's token table is different than the independent value within the tokenCache.
            // This is clearly a bug, but I can't work out the flow.
            // Instead of fixing the issue, imma just hack around it.  Hackity hackity hackity.
            // There's...like...no chance this'll come back and bite me, right?
            val tokensFromCache = tokenCache.get(sourceName)
            val tokenTableFromCache = tokensFromCache?.tokens?.tokenStore?.tokenTable(sourceName)
            tokenTableFromCache?.row(closestLineWithContent) ?: it
         } else {
            it
         }
      } as SortedMap
      if (row.isEmpty()) {
         return null
      }
      val tokenStartIndices = row.keys as SortedSet
      val nearestStartIndex = tokenStartIndices.takeWhile { startIndex -> startIndex <= char }.lastOrNull()
      val nearestToken = nearestStartIndex?.let { index -> row.get(index) }
         ?.let { ruleContext -> searchWithinTokenChildren(ruleContext, closestLineWithContent, char) }

         ?.let { ruleContext ->
            // If the token we hit was synthetic (ie.,
            // created during the compilation process to recover from errors),
            // then return the token that came before it.
            if (syntheticTokens.contains(ruleContext.start)) {
               ruleContext.parent as ParserRuleContext?
            } else {
               ruleContext
            }
         }



      return nearestToken
   }

   private tailrec fun searchBackwardsForClosestLine(
      tokenTable: Table<RowIndex, ColumnIndex, ParserRuleContext>,
      zeroBasedLineIndex: Int
   ): Int? {
      if (zeroBasedLineIndex < 0) return null
      if (tokenTable.rowKeySet().contains(zeroBasedLineIndex)) {
         return zeroBasedLineIndex
      } else {
         return searchBackwardsForClosestLine(tokenTable, zeroBasedLineIndex - 1)
      }
   }

   private fun searchWithinTokenChildren(
      token: ParserRuleContext,
      zeroBasedLineIndex: Int,
      char: Int
   ): ParserRuleContext {
      val tokens = generateSequence(token.children) { token ->
         val next = token
            .filterIsInstance<ParserRuleContext>()
            .flatMap { it.children ?: emptyList() }
            .filterIsInstance<ParserRuleContext>()
            // Line indexes are 1 based when coming from the compiler
            .filter { (it.start.line - 1) <= zeroBasedLineIndex && it.start.charPositionInLine < char }
         if (next.isNotEmpty()) {
            next
         } else {
            null
         }
      }.toList().flatten()
      return tokens
         .filterIsInstance<ParserRuleContext>()
         .lastOrNull() ?: token
   }


   /**
    * When an exact match isn't possible from location, (ie., because of cmpilation errors),
    * look for the nearest possible context based on the source location.
    */
   fun getNearestToken(line: Int, char: Int, sourceName: String = UNKNOWN_SOURCE): ParseTree? {
      val tokenTable = tokens.tokenStore.tokenTable(sourceName)

      // we can't look up directly from the line number, as some tokens will span multiple lines
      // (especially if there are compiler errors)
      val nearestRow = tokenTable.rowKeySet()
         .takeWhile { rowIndex -> rowIndex <= line }.lastOrNull()
         ?.let { rowIndex -> tokenTable.row(rowIndex) }
         ?: return null

      val nearestTree = nearestRow.values
         .firstOrNull { token -> token.containsLocation(line + 1, char) }
         ?.childAtLocation(line + 1, char)

      return nearestTree
   }

   fun containsTokensForSource(sourceName: String): Boolean {
      return tokens.tokenStore.containsTokensForSource(sourceName)
   }

   data class CollectedTokens(
      val tokens: Tokens,
      val errors: List<CompilationError>,
      val syntheticTokens: List<Token>
   )

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
   @OptIn(ExperimentalTime::class)
   private fun collectTokens(): CollectedTokens {
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
      val syntheticTokens = collectionResult.flatMap { it.syntheticTokens }
      val timedTokens = measureTimedValue {
         Tokens.combine(tokensCollection)
      }
      return CollectedTokens(
         timedTokens.value,
         errors,
         syntheticTokens
      )
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
      return tokenProcessorWithImports.tokens.tokenStore.getTypeReferencesForSourceName(sourceName).mapNotNull {
         try {
            tokenProcessorWithImports.lookupSymbolByName(it)
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

   val text = if (this.start.startIndex > this.stop.stopIndex) {
      // This shouldn't really happen, unless there's a parse error in the source we were given,
      // However, since we don't have anything to work off, return the whole text
      this.start.inputStream.toString()
   } else {
      this.start.inputStream.getText(Interval(this.start.startIndex, this.stop.stopIndex))
   }
   val origin = this.start.inputStream.sourceName
   return SourceCode(origin, text)
}

fun TaxiParser.LiteralContext.isNullValue(): Boolean {
   return this.text == "null"
}


interface NamespaceQualifiedTypeResolver {
   val namespace: String
   fun resolve(context: TaxiParser.TypeReferenceContext): Either<List<CompilationError>, Type>

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
      .map { QualifiedName.from(it.qualifiedName().identifier().text()) }
   return imports
}


fun RuleContext.searchUpForRule(ruleType: Class<out RuleContext>): RuleContext? =
   searchUpForRule(listOf(ruleType))

inline fun <reified T : RuleContext> RuleContext.searchUpForRule(): T? =
   searchUpForRule(listOf(T::class.java)) as T?

fun RuleContext.searchUpExcluding(vararg ruleTypes: Class<out RuleContext>): ParserRuleContext? {
   val parent = this.parent ?: return null
   return if (ruleTypes.contains(parent::class.java)) {
      parent.searchUpExcluding(*ruleTypes)
   } else {
      parent as ParserRuleContext
   }
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
      is TaxiParser.NamespaceDeclarationContext -> return namespaceRule.qualifiedName().identifier().text()
      is TaxiParser.NamespaceBlockContext -> return namespaceRule.children.filterIsInstance<TaxiParser.QualifiedNameContext>()
         .first().identifier().text()

      is TaxiParser.SingleNamespaceDocumentContext -> namespaceRule.children.filterIsInstance<TaxiParser.NamespaceDeclarationContext>()
         .firstOrNull()?.qualifiedName()?.identifier()?.text()
         ?: Namespaces.DEFAULT_NAMESPACE

      else -> Namespaces.DEFAULT_NAMESPACE
   }
}

fun TaxiParser.NamespaceDeclarationContext.namespace(): String {
   return this.qualifiedName().identifier().text()
}

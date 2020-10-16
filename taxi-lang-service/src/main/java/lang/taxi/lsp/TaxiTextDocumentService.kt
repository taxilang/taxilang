package lang.taxi.lsp

import lang.taxi.*
import lang.taxi.lsp.actions.CodeActionService
import lang.taxi.lsp.completion.CompletionService
import lang.taxi.lsp.completion.TypeProvider
import lang.taxi.lsp.formatter.FormatterService
import lang.taxi.lsp.gotoDefinition.GotoDefinitionService
import lang.taxi.lsp.hover.HoverService
import lang.taxi.lsp.linter.LintingService
import lang.taxi.types.SourceNames
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.TextDocumentService
import java.io.File
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference


/**
 * Stores the compiled snapshot for a file
 * Contains both the TaxiDocument - for accessing types, etc,
 * and the compiler, for accessing tokens and compiler context - useful
 * for completion
 */
data class CompilationResult(val compiler: Compiler, val document: TaxiDocument?) {
    val successful = document != null
}


class TaxiTextDocumentService() : TextDocumentService, LanguageClientAware {

    private var displayedMessages: List<PublishDiagnosticsParams> = emptyList()
    private val sources: MutableMap<URI, String> = mutableMapOf()
    private val charStreams: MutableMap<URI, CharStream> = mutableMapOf()
    private lateinit var initializeParams: InitializeParams
    private val lastSuccessfulCompilationResult: AtomicReference<CompilationResult> = AtomicReference();
    val lastCompilationResult: AtomicReference<CompilationResult> = AtomicReference();
    private val tokenCache: CompilerTokenCache = CompilerTokenCache()

    // TODO : We can probably use the unparsedTypes from the tokens for this, rather than the
    // types themselves, as it'll give better results sooner
    private val typeProvider = TypeProvider(lastSuccessfulCompilationResult, lastCompilationResult)
    private val completionService = CompletionService(typeProvider)
    private val formattingService = FormatterService()
    private val gotoDefinitionService = GotoDefinitionService(typeProvider)
    private val hoverService = HoverService()
    private val codeActionService = CodeActionService()
    private val lintingService = LintingService()

    private lateinit var client: LanguageClient
    private var rootUri: String? = null

    private var initialized: Boolean = false
    private var connected: Boolean = false

    private val ready: Boolean
        get() {
            return initialized && connected
        }

    var compilerMessages: List<CompilationError> = emptyList()
        private set

    var compilerErrorDiagnostics: Map<String, List<Diagnostic>> = emptyMap()
    var linterDiagnostics: Map<String, List<Diagnostic>> = emptyMap()

    override fun codeAction(params: CodeActionParams): CompletableFuture<MutableList<Either<Command, CodeAction>>> {
        return codeActionService.getActions(params)
    }

    override fun completion(position: CompletionParams): CompletableFuture<Either<MutableList<CompletionItem>, CompletionList>> {
        if (lastCompilationResult.get() == null) {
            compile()
        }
        return completionService.computeCompletions(lastCompilationResult.get(), position)
    }

    override fun definition(params: DefinitionParams): CompletableFuture<Either<MutableList<out Location>, MutableList<out LocationLink>>> {
        if (lastCompilationResult.get() == null) {
            compile()
        }
        return gotoDefinitionService.definition(lastCompilationResult.get(), params)
    }

    override fun hover(params: HoverParams): CompletableFuture<Hover> {
        if (lastCompilationResult.get() == null) {
            compile()
        }
        return hoverService.hover(lastCompilationResult.get(), lastSuccessfulCompilationResult.get(), params)
    }

    override fun formatting(params: DocumentFormattingParams): CompletableFuture<MutableList<out TextEdit>> {
        val uri = URI.create(SourceNames.normalize(params.textDocument.uri))
        val content = this.sources[uri]
                ?: error("Could not find source with url ${params.textDocument.uri}")
        return formattingService.getChanges(content, params.options)
    }

    override fun didOpen(params: DidOpenTextDocumentParams) {
        computeLinterMessages(params.textDocument.uri)
        publishDiagnosticMessages()
    }

    private fun computeLinterMessages(documentUri: String) {
        val normalizedUri = SourceNames.normalize(documentUri)
        val uri = URI.create(normalizedUri)
        this.linterDiagnostics = mapOf(normalizedUri to lintingService.computeInsightFor(uri, lastCompilationResult.get()))
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        if (params.contentChanges.size > 1) {
            error("Multiple changes not supported yet")
        }
        val change = params.contentChanges.first()
        if (change.range != null) {
            error("Ranged changes not yet supported")
        }
        val content = change.text
        val sourceName = params.textDocument.uri

        // This seems to normalize nicely on windows.  Will need to check on ubuntu
        val sourceNameUri = URI.create(SourceNames.normalize(sourceName))
        this.sources[sourceNameUri] = content
        this.charStreams[sourceNameUri] = CharStreams.fromString(content, sourceName)
        compile()
        computeLinterMessages(params.textDocument.uri)
        publishDiagnosticMessages()
    }

    // This is a very non-performant first pass.
    // We're compiling the entire workspace every time we get a request, which is
    // on every keypress.
    // We need to find a way to only recompile the document that has changed
    internal fun compile(): CompilationResult {
        val charStreams = this.charStreams.values.toList()

        val compiler = Compiler(charStreams, tokenCache = tokenCache)

        try {
            val compiled = compiler.compile()
            val compilationResult = CompilationResult(compiler, compiled)
            lastSuccessfulCompilationResult.set(compilationResult)
            lastCompilationResult.set(compilationResult)
            compilerMessages = emptyList()
        } catch (e: CompilationException) {
            compilerMessages = e.errors
            val compilationResult = CompilationResult(compiler, null)
            lastCompilationResult.set(compilationResult)
        }
        recomputeCompilerMessages()

        return lastCompilationResult.get()
    }

    private fun recomputeCompilerMessages() {
        val diagnostics = this.compilerMessages.map { error ->
            // Note - for VSCode, we can use the same position for start and end, and it
            // highlights the entire word
            val position = Position(
                    error.line - 1,
                    error.char
            )
            (error.sourceName ?: "Unknown source") to Diagnostic(
                    Range(position, position),
                    error.detailMessage ?: "Unknown error",
                    DiagnosticSeverity.Error,
                    "Compiler"
            )
        }
        this.compilerErrorDiagnostics = diagnostics.groupBy({ it.first }, { it.second })
                .mapKeys { (fileUri, _) -> SourceNames.normalize(fileUri) }
    }

    private fun publishDiagnosticMessages() {
        val diagnosticMessages = (compilerErrorDiagnostics.keys + linterDiagnostics.keys).map { uri ->
            val normalisedUrl = SourceNames.normalize(uri)
            normalisedUrl to (compilerErrorDiagnostics.getOrDefault(normalisedUrl, emptyList()) + linterDiagnostics.getOrDefault(normalisedUrl, emptyList()))
        }.map { (uri, diagnostics) ->
            // When sending diagnostic messages, use the canonical path of the file, rather than
            // the normalized URI.  This means we get an OS specific file.
            // VSCode on windows seems to not like the URI

            val filePath = File(URI.create(uri)).canonicalPath
            PublishDiagnosticsParams(uri, diagnostics)
        }

        clearErrors()
        this.displayedMessages = diagnosticMessages


        diagnosticMessages.forEach {
            client.publishDiagnostics(it)
        }
    }

    private fun clearErrors() {
        if (connected) {
            // Non-performant - we're destroying the entire set of warnings for each compilation pass
            // (which in practice, is each keypress)
            this.displayedMessages.forEach { oldErrorMessage ->
                client.publishDiagnostics(PublishDiagnosticsParams(oldErrorMessage.uri, emptyList()))
            }
            this.displayedMessages = emptyList()
        }
    }

    override fun connect(client: LanguageClient) {
        this.client = client
        connected = true
        if (ready) {
            compile()
            publishDiagnosticMessages()

        }
    }

    fun initialize(params: InitializeParams) {
        this.rootUri = params.rootUri
        this.initializeParams = params

        val initSources = File(URI.create(params.rootUri))
                .walk()
                .filter { it.extension == "taxi" && !it.isDirectory }
                .map {
                    val source = it.readText()
                    it.toURI() to (source to CharStreams.fromString(source, it.toPath().toString()))
                }
                .toMap()


        initSources.forEach { (key, sourceAndCharStream) ->
            val (source, charStream) = sourceAndCharStream
            this.charStreams[key] = charStream
            this.sources[key] = source
        }
        initialized = true

        if (ready) {
            client.logMessage(MessageParams(MessageType.Log, "Found ${charStreams.size} to compile on startup"))
            compile()
        }

    }
}
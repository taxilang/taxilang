package lang.taxi.lsp

import lang.taxi.*
import lang.taxi.lsp.completion.CompletionService
import lang.taxi.lsp.completion.TypeProvider
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

    private val sources: MutableMap<URI, CharStream> = mutableMapOf()
    private lateinit var initializeParams: InitializeParams
    private val lastSuccessfulCompilationResult: AtomicReference<CompilationResult> = AtomicReference();
    private val lastCompilationResult: AtomicReference<CompilationResult> = AtomicReference();
    private val tokenCache: CompilerTokenCache = CompilerTokenCache()

    // TODO : We can probably use the unparsedTypes from the tokens for this, rather than the
    // types themselves, as it'll give better results sooner
    private val typeProvider = TypeProvider(lastSuccessfulCompilationResult, lastCompilationResult)
    private val completionService = CompletionService(typeProvider)
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


    override fun completion(position: CompletionParams): CompletableFuture<Either<MutableList<CompletionItem>, CompletionList>> {
        if (lastCompilationResult.get() == null) {
            compileAndReport()
        }
        return completionService.computeCompletions(lastCompilationResult.get(), position)
    }

    override fun didOpen(params: DidOpenTextDocumentParams) {
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
        val uri = sourceName

        this.sources[URI.create(uri)] = CharStreams.fromString(content, uri)
        compileAndReport()


    }

    // This is a very non-performant first pass.
    // We're compiling the entire workspace every time we get a request, which is
    // on every keypress.
    // We need to find a way to only recompile the document that has changed
    private fun compileAndReport() {
        val charStreams = this.sources.values.toList()
        val compiler = Compiler(charStreams, tokenCache = tokenCache)

        try {
            val compiled = compiler.compile()
            val compilationResult = CompilationResult(compiler, compiled)
            lastSuccessfulCompilationResult.set(compilationResult)
            lastCompilationResult.set(compilationResult)
            compilerMessages = emptyList()
            clearErrors()
        } catch (e: CompilationException) {
            compilerMessages = e.errors
            val compilationResult = CompilationResult(compiler, null)
            lastCompilationResult.set(compilationResult)
        }
        reportMessages()
    }

    private fun reportMessages() {
        if (!connected) {
            return
        }
        val diagnostics = this.compilerMessages.map { error ->
            // Note - for VSCode, we can use the same position for start and end, and it
            // highlights the entire word
            val position = Position(
                    error.offendingToken.line - 1,
                    error.offendingToken.charPositionInLine
            )
            (error.sourceName ?: "Unknown source") to Diagnostic(
                    Range(position, position),
                    error.detailMessage ?: "Unknown error",
                    DiagnosticSeverity.Error,
                    "Compiler"
            )
        }
        clearErrors()
        diagnostics.groupBy { it.first }.forEach { (sourceUri, diagnostics) ->
            client.publishDiagnostics(PublishDiagnosticsParams(
                    sourceUri,
                    diagnostics.map { it.second }
            ))
        }
    }

    private fun clearErrors() {
        if (connected) {
            // Non-performant - we're destroying the entire set of warnings for each compilation pass
            // (which in practice, is each keypress)
            this.sources.keys.forEach { sourceUri ->
                client.publishDiagnostics(PublishDiagnosticsParams(
                        sourceUri.toString(),
                        emptyList()
                ))
            }
        }
    }

    override fun connect(client: LanguageClient) {
        this.client = client
        connected = true
        if (ready) {
            compileAndReport()
        }
    }

    fun initialize(params: InitializeParams) {
        this.rootUri = params.rootUri
        this.initializeParams = params

        val initSources = File(URI.create(params.rootUri))
                .walk()
                .filter { it.extension == "taxi" }
                .map { it.toURI() to CharStreams.fromPath(it.toPath()) }
                .toMap()

        this.sources.putAll(initSources)
        initialized = true

        if (ready) {
            client.logMessage(MessageParams(MessageType.Log, "Found ${sources.size} to compile on startup"))
            compileAndReport()
        }

    }
}
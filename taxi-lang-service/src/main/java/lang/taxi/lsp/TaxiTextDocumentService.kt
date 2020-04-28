package lang.taxi.lsp

import lang.taxi.CompilationError
import lang.taxi.CompilationException
import lang.taxi.Compiler
import lang.taxi.TaxiDocument
import lang.taxi.lsp.completion.CompletionService
import lang.taxi.lsp.completion.TypeProvider
import lang.taxi.lsp.completion.completions
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
data class CompiledFile(val compiler: Compiler, val document: TaxiDocument)

class TaxiTextDocumentService() : TextDocumentService, LanguageClientAware {

    private val sources: MutableMap<URI, CharStream> = mutableMapOf()
    private lateinit var initializeParams: InitializeParams
    private val masterDocument: AtomicReference<TaxiDocument> = AtomicReference();
    private val compiledDocuments: MutableMap<String, CompiledFile> = mutableMapOf()
    private val typeProvider = TypeProvider(masterDocument)
    private val completionService = CompletionService(typeProvider)
    private lateinit var client: LanguageClient
    private var rootUri: String? = null

    var compilerMessages: List<CompilationError> = emptyList()
        private set


    override fun completion(position: CompletionParams): CompletableFuture<Either<MutableList<CompletionItem>, CompletionList>> {
        val file = compiledDocuments[position.textDocument.uri]
                ?: return completions()
        return completionService.computeCompletions(file, position)
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
        try {
            val charStreams = this.sources.values.toList()
            val compiler = Compiler(charStreams)
            val compiled = compiler.compile()
            masterDocument.set(compiled)
            compilerMessages = emptyList()
            clearErrors()
        } catch (e: CompilationException) {
            compilerMessages = e.errors
        }
        reportMessages()
    }

    private fun reportMessages() {
        if (!this::client.isInitialized) {
            return
        }
        val diagnostics = this.compilerMessages.map { error ->
            // Note - for VSCode, we can use the same position for start and end, and it
            // highlights the entire word
            val position = Position(
                    error.offendingToken.line,
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
        if (this::client.isInitialized) {
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
        if (this::client.isInitialized) {
            client.logMessage(MessageParams(MessageType.Log, "Found ${sources.size} to compile on startup"))
        }
        compileAndReport()
    }
}
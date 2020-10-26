package lang.taxi.lsp

import lang.taxi.CompilationError
import lang.taxi.Compiler
import lang.taxi.CompilerTokenCache
import lang.taxi.TaxiDocument
import lang.taxi.lsp.actions.CodeActionService
import lang.taxi.lsp.completion.CompletionService
import lang.taxi.lsp.formatter.FormatterService
import lang.taxi.lsp.gotoDefinition.GotoDefinitionService
import lang.taxi.lsp.hover.HoverService
import lang.taxi.lsp.linter.LintingService
import lang.taxi.types.SourceNames
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.TextDocumentService
import java.io.File
import java.net.URI
import java.util.concurrent.CompletableFuture


/**
 * Stores the compiled snapshot for a file
 * Contains both the TaxiDocument - for accessing types, etc,
 * and the compiler, for accessing tokens and compiler context - useful
 * for completion
 */
data class CompilationResult(val compiler: Compiler, val document: TaxiDocument?, val errors: List<CompilationError> = emptyList()) {
    val successful = document != null
}


class TaxiTextDocumentService(private val compilerService: TaxiCompilerService) : TextDocumentService, LanguageClientAware {

    val lastCompilationResult: CompilationResult
        get() {
            return this.compilerService.getOrComputeLastCompilationResult()
        }
    private var displayedMessages: List<PublishDiagnosticsParams> = emptyList()
    private lateinit var initializeParams: InitializeParams
    private val tokenCache: CompilerTokenCache = CompilerTokenCache()

    // TODO : We can probably use the unparsedTypes from the tokens for this, rather than the
    // types themselves, as it'll give better results sooner

    private val completionService = CompletionService(compilerService.typeProvider)
    private val formattingService = FormatterService()
    private val gotoDefinitionService = GotoDefinitionService(compilerService.typeProvider)
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
        val lastCompilationResult = compilerService.getOrComputeLastCompilationResult()
        return completionService.computeCompletions(lastCompilationResult, position)
    }
    override fun definition(params: DefinitionParams): CompletableFuture<Either<MutableList<out Location>, MutableList<out LocationLink>>> {
        val lastCompilationResult = compilerService.getOrComputeLastCompilationResult()
        return gotoDefinitionService.definition(lastCompilationResult, params)
    }

    override fun hover(params: HoverParams): CompletableFuture<Hover> {
        val lastCompilationResult = compilerService.getOrComputeLastCompilationResult()
        return hoverService.hover(lastCompilationResult, compilerService.lastSuccessfulCompilationResult.get(), params)
    }

    override fun formatting(params: DocumentFormattingParams): CompletableFuture<MutableList<out TextEdit>> {
        val content = compilerService.source(params.textDocument.uri)
        return formattingService.getChanges(content, params.options)
    }

    override fun didOpen(params: DidOpenTextDocumentParams) {
        computeLinterMessages(params.textDocument.uri)
        publishDiagnosticMessages()
    }

    private fun computeLinterMessages(documentUri: String) {
        val normalizedUri = SourceNames.normalize(documentUri)
        val uri = URI.create(normalizedUri)
        this.linterDiagnostics = mapOf(normalizedUri to lintingService.computeInsightFor(uri, compilerService.getOrComputeLastCompilationResult()))
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

        compilerService.updateSource(params.textDocument, content)

        compile()
        computeLinterMessages(params.textDocument.uri)
        publishDiagnosticMessages()
    }

    // This is a very non-performant first pass.
    // We're compiling the entire workspace every time we get a request, which is
    // on every keypress.
    // We need to find a way to only recompile the document that has changed
    internal fun compile(): CompilationResult {
        val compilationResult = this.compilerService.compile()
        this.compilerMessages = compilationResult.errors
        this.compilerErrorDiagnostics = convertCompilerMessagesToDiagnotics(this.compilerMessages)

        return compilationResult
    }

    private fun convertCompilerMessagesToDiagnotics(compilerMessages: List<CompilationError>): Map<String, List<Diagnostic>> {
        val diagnostics = compilerMessages.map { error ->
            // Note - for VSCode, we can use the same position for start and end, and it
            // highlights the entire word
            val position = Position(
                    error.line - 1,
                    error.char
            )
            val severity:DiagnosticSeverity = when(error.severity) {
                CompilationError.Severity.INFO -> DiagnosticSeverity.Information
                CompilationError.Severity.WARNING -> DiagnosticSeverity.Warning
                CompilationError.Severity.ERROR -> DiagnosticSeverity.Error
            }
            (error.sourceName ?: "Unknown source") to Diagnostic(
                    Range(position, position),
                    error.detailMessage,
                    severity,
                    "Compiler"
            )
        }
        return diagnostics.groupBy({ it.first }, { it.second })
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

        File(URI.create(params.rootUri))
                .walk()
                .filter { it.extension == "taxi" && !it.isDirectory }
                .forEach {
                    val source = it.readText()
                    // Note - use the uri from the path, not the file, to ensure consistency.
                    // on windows, file uri's are file:///C:/ ... and path uris are file:///c:/...
                    compilerService.updateSource(SourceNames.normalize(SourceNames.normalize(it.toPath().toString())), source)
                }

        initialized = true

        if (ready) {
            client.logMessage(MessageParams(MessageType.Log, "Found ${compilerService.sourceCount} to compile on startup"))
            compile()
        }

    }
}
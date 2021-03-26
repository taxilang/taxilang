package lang.taxi.lsp

import lang.taxi.CompilationError
import lang.taxi.Compiler
import lang.taxi.CompilerTokenCache
import lang.taxi.TaxiDocument
import lang.taxi.UnknownTokenReferenceException
import lang.taxi.lsp.actions.CodeActionService
import lang.taxi.lsp.completion.CompletionService
import lang.taxi.lsp.formatter.FormatterService
import lang.taxi.lsp.gotoDefinition.GotoDefinitionService
import lang.taxi.lsp.hover.HoverService
import lang.taxi.lsp.linter.LintingService
import lang.taxi.messages.Severity
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
import org.eclipse.lsp4j.MarkupContent
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
import java.time.Duration
import java.util.concurrent.CompletableFuture

object UnknownSource {
    val UNKNOWN_SOURCE = "Unknown source"
}

/**
 * Stores the compiled snapshot for a file
 * Contains both the TaxiDocument - for accessing types, etc,
 * and the compiler, for accessing tokens and compiler context - useful
 * for completion
 */
data class CompilationResult(
    val compiler: Compiler,
    val document: TaxiDocument?,
    val countOfSources: Int,
    val duration: Duration,
    val errors: List<CompilationError> = emptyList()


) {
    val successful = document != null
}

class TaxiTextDocumentService(private val compilerService: TaxiCompilerService) : TextDocumentService,
    LanguageClientAware {

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

    init {

        compilerService.compilationResults
            .subscribe { compilationResult ->
                logCompilationResult(compilationResult)
                this.compilerMessages = compilationResult.errors
                this.compilerErrorDiagnostics = convertCompilerMessagesToDiagnotics(this.compilerMessages)
                publishDiagnosticMessages()
            }
    }

    private val ready: Boolean
        get() {
            return initialized && connected
        }

    var compilerMessages: List<CompilationError> = emptyList()
        private set

    private var compilerErrorDiagnostics: Map<String, List<Diagnostic>> = emptyMap()
    private var linterDiagnostics: Map<String, List<Diagnostic>> = emptyMap()

    override fun codeAction(params: CodeActionParams): CompletableFuture<MutableList<Either<Command, CodeAction>>> {
        return codeActionService.getActions(params)
    }

    override fun completion(position: CompletionParams): CompletableFuture<Either<MutableList<CompletionItem>, CompletionList>> {
        if (position.textDocument.uri.endsWith(".conf")) {
            return CompletableFuture.completedFuture(Either.forLeft(mutableListOf()))
        }
        val lastCompilationResult = compilerService.getOrComputeLastCompilationResult()
        return completionService.computeCompletions(lastCompilationResult, position)
    }

    override fun definition(params: DefinitionParams): CompletableFuture<Either<MutableList<out Location>, MutableList<out LocationLink>>> {
        if (params.textDocument.uri.endsWith(".conf")) {
            return CompletableFuture.completedFuture(Either.forLeft(mutableListOf()))
        }
        val lastCompilationResult = compilerService.getOrComputeLastCompilationResult()
        return gotoDefinitionService.definition(lastCompilationResult, params)
    }

    override fun hover(params: HoverParams): CompletableFuture<Hover> {
        if (params.textDocument.uri.endsWith(".conf")) {
            return CompletableFuture.completedFuture(Hover(MarkupContent("markdown", "")))
        }
        val lastCompilationResult = compilerService.getOrComputeLastCompilationResult()
        return try {
            hoverService.hover(lastCompilationResult, compilerService.lastSuccessfulCompilationResult.get(), params)
        } catch (unknownTokenException: UnknownTokenReferenceException) {
            // Try to reload just the once, and then re-attempt the hover call.
            // If it fails after that, then just let the exception get thrown to prevent
            // looping forever
            forceReload("Received a reference to an unknown file - ${unknownTokenException.providedSourcePath}")
            hoverService.hover(lastCompilationResult, compilerService.lastSuccessfulCompilationResult.get(), params)
        }
    }

    override fun formatting(params: DocumentFormattingParams): CompletableFuture<MutableList<out TextEdit>> {
        if (params.textDocument.uri.endsWith(".conf")) {
            return CompletableFuture.completedFuture(mutableListOf())
        }
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
        this.linterDiagnostics = mapOf(
            normalizedUri to
                    lintingService.computeInsightFor(uri, compilerService.getOrComputeLastCompilationResult())
        )
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        // We only retrigger full reload on save of a the taxi.conf
        // as it's an expensive operation.
        val sourceName = params.textDocument.uri
        if (sourceName.endsWith("taxi.conf")) {
            forceReload("taxi.conf has changed - reloading")
        }
    }

    private fun forceReload(reason: String) {
        client.logMessage(
            MessageParams(
                MessageType.Info,
                reason
            )
        )
        compilerService.reloadSourcesAndTriggerCompilation()
//        compile()
//        publishDiagnosticMessages()
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


        if (sourceName.endsWith("taxi.conf")) {
            // SKip it, we'll wait for a save.
        } else {
            compilerService.updateSource(params.textDocument, content)
            this.compilerService.triggerAsyncCompilation()
        }
    }

    // This is a very non-performant first pass.
    // We're compiling the entire workspace every time we get a request, which is
    // on every keypress.
    // We need to find a way to only recompile the document that has changed
    @Deprecated("Use CompilerService.triggerAsyncCompilation")
    internal fun compile(): CompilationResult {
        val compilationResult = this.compilerService.compile()
        logCompilationResult(compilationResult)
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
            val severity: DiagnosticSeverity = when (error.severity) {
                Severity.INFO -> DiagnosticSeverity.Information
                Severity.WARNING -> DiagnosticSeverity.Warning
                Severity.ERROR -> DiagnosticSeverity.Error
            }
            (error.sourceName ?: UnknownSource.UNKNOWN_SOURCE) to Diagnostic(
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
            normalisedUrl to (compilerErrorDiagnostics.getOrDefault(
                normalisedUrl,
                emptyList()
            ) + linterDiagnostics.getOrDefault(normalisedUrl, emptyList()))
        }.map { (uri, diagnostics) ->
            // When sending diagnostic messages, use the canonical path of the file, rather than
            // the normalized URI.  This means we get an OS specific file.
            // VSCode on windows seems to not like the URI

            val filePath = try {
                File(URI.create(uri)).canonicalPath
            } catch (e: Exception) {
                UnknownSource.UNKNOWN_SOURCE
            }
            PublishDiagnosticsParams(filePath, diagnostics)
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
            initializeCompilerService()
        }
    }

    fun initialize(params: InitializeParams) {
        this.rootUri = params.rootUri
        this.initializeParams = params

        initialized = true

        if (ready) {
            initializeCompilerService()
        }
    }

    internal fun initializeCompilerService() {
        this.compilerService.initialize(rootUri!!, client)
        this.compilerService.reloadSourcesWithoutCompiling()
        compile()
        publishDiagnosticMessages()
    }

    private fun logCompilationResult(result: CompilationResult) {
        client.logMessage(
            MessageParams(
                MessageType.Log,
                "Compiled ${result.countOfSources} sources in ${result.duration.toMillis()}ms"
            )
        )
    }
}
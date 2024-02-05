package lang.taxi.lsp

import lang.taxi.CompilationError
import lang.taxi.UnknownTokenReferenceException
import lang.taxi.lsp.sourceService.WorkspaceSourceService
import lang.taxi.lsp.sourceService.isWebIdeUri
import lang.taxi.packages.MalformedTaxiConfFileException
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
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.SignatureHelpParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.TextDocumentService
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CompletableFuture

object UnknownSource {
   // Must be a URI, or code services blow up
   const val UNKNOWN_SOURCE = "inmemory://unknown-source"
}

/**
 * A wrapper for a group of messages we want to display in the "Problems" panel inside VSCode
 */
interface DiagnosticMessagesWrapper {
   val countOfSources: Int
   val duration: Duration
   val messages: Map<String, List<Diagnostic>>
}

class UnreadableTaxiConfMessage(private val path: Path, private val message: String, private val lineNumber: Int?) :
   DiagnosticMessagesWrapper {
   override val countOfSources: Int = 1
   override val duration: Duration = Duration.ofSeconds(0)
   override val messages: Map<String, List<Diagnostic>>
      get() {
         val position = Position(lineNumber ?: 1, 1)
         val sourceName = SourceNames.normalize(path)
         val messages = listOf(
            Diagnostic(
               Range(position, position),
               message,
               DiagnosticSeverity.Error,
               sourceName
            )
         )
         return mapOf(sourceName to messages)
      }


}

/**
 * Triggers compilation from a changed path.
 * Note - use a URI, not a Path here, as the changed path may be
 * an imnemory:// file, when using a web-client LSP client.
 */
data class CompilationTrigger(val changedPath: URI?)

class TaxiTextDocumentService(services: LspServicesConfig) : TextDocumentService,
   LanguageClientAware {
   constructor(compilerService: TaxiCompilerService) : this(LspServicesConfig(compilerService = compilerService))

   val lastCompilationResult: CompilationResult
      get() {
         return this.compilerService.getOrComputeLastCompilationResult()
      }

   private var displayedMessages: List<PublishDiagnosticsParams> = emptyList()
   private lateinit var initializeParams: InitializeParams

   private val compilerService = services.compilerService
   private val completionService = services.completionService
   private val formattingService = services.formattingService
   private val gotoDefinitionService = services.gotoDefinitionService
   private val hoverService = services.hoverService
   private val codeActionService = services.codeActionService
   private val lintingService = services.lintingService
   private val signatureHelpService = services.signatureHelpService
   private lateinit var client: LanguageClient
   private var rootUri: String? = null

   private var initialized: Boolean = false
   private var connected: Boolean = false

   init {

      compilerService.compilationResults
         .subscribe { compilationResult ->
            logCompilationResult(compilationResult)
            if (compilationResult is CompilationResult) {
               this.compilerMessages = compilationResult.errors
            }
            this.compilerErrorDiagnostics = compilationResult.messages
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
      val lastCompilationResult =
         compilerService.getOrComputeLastCompilationResult(uriToAssertIsPreset = params.textDocument.uri)

      return codeActionService.getActions(lastCompilationResult, params)
   }

   override fun completion(position: CompletionParams): CompletableFuture<Either<MutableList<CompletionItem>, CompletionList>> {
      if (position.textDocument.uri.endsWith(".conf")) {
         return CompletableFuture.completedFuture(Either.forLeft(mutableListOf()))
      }
      val lastCompilationResult =
         compilerService.getOrComputeLastCompilationResult(uriToAssertIsPreset = position.textDocument.uri)
      return completionService.computeCompletions(
         lastCompilationResult,
         position,
         compilerService.lastSuccessfulCompilation()
      )

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
         hoverService.hover(lastCompilationResult, compilerService.lastSuccessfulCompilation(), params)
      } catch (unknownTokenException: UnknownTokenReferenceException) {
         // Try to reload just the once, and then re-attempt the hover call.
         // If it fails after that, then just let the exception get thrown to prevent
         // looping forever
         forceReload("Received a reference to an unknown file - ${unknownTokenException.providedSourcePath}")
         hoverService.hover(lastCompilationResult, compilerService.lastSuccessfulCompilation(), params)
      }
   }

   override fun signatureHelp(params: SignatureHelpParams): CompletableFuture<SignatureHelp> {
      val lastCompilationResult =
         compilerService.getOrComputeLastCompilationResult(uriToAssertIsPreset = params.textDocument.uri)
      return signatureHelpService.getSignatureHelp(
         lastCompilationResult,
         compilerService.lastSuccessfulCompilation(),
         params
      )
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

      // The user has just opened a new file in a browser.  Create it as an empty file
      // This prevents errors later, when attempts to complete are made.
      if (isWebIdeUri(params.textDocument.uri)) {
         triggerCompilation(params.textDocument.uri, params.textDocument.text)
      }
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

   fun forceReload(reason: String) {
      client.logMessage(
         MessageParams(
            MessageType.Info,
            reason
         )
      )
      compilerService.reloadSourcesAndTriggerCompilation()
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
         triggerCompilation(params.textDocument, content)
      }
   }

   private fun triggerCompilation(textDocument: TextDocumentIdentifier, content: String) {
      triggerCompilation(textDocument.uri, content)
   }

   private fun triggerCompilation(textDocumentUri: String, content: String) {
      val compilationTrigger = CompilationTrigger(
         URI.create(SourceNames.normalize(textDocumentUri))
      )
      compilerService.updateSource(textDocumentUri, content)
      this.compilerService.triggerAsyncCompilation(compilationTrigger)
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
      this.compilerErrorDiagnostics = compilationResult.messages

      return compilationResult
   }

   /**
    * publishes diagnostic messages (ie., the messages that appear in the "Problems" panel in VSCode)
    * out to the LanguageClient.
    *
    * Note: This is a destructive operation - any existing messages are replaced.
    */
   fun publishDiagnosticMessages(messages: List<Pair<String, List<Diagnostic>>>) {
      val diagnosticMessages = messages.map { (uri, diagnostics) ->
         // When sending diagnostic messages, use the canonical path of the file, rather than
         // the normalized URI.  This means we get an OS specific file.
         // VSCode on windows seems to not like the URI

         val filePath = try {
            val parsedURI = URI.create(uri)
            when (parsedURI.scheme) {
               "file" -> File(parsedURI).canonicalPath
               "inmemory" -> uri
               else -> uri
            }
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

   private fun publishDiagnosticMessages() {
      val diagnosticMessages = (compilerErrorDiagnostics.keys + linterDiagnostics.keys).map { uri ->
         val normalisedUrl = SourceNames.normalize(uri)
         normalisedUrl to (compilerErrorDiagnostics.getOrDefault(
            normalisedUrl,
            emptyList()
         ) + linterDiagnostics.getOrDefault(normalisedUrl, emptyList()))
      };
      publishDiagnosticMessages(diagnosticMessages)

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
         // Not sure if this is true, but it makes this sequence trickier
//         error("Can't connect after initialze - should be other way around.")
//            initializeCompilerService(workspaceSourceService)
      }
   }

   fun initialize(params: InitializeParams, workspaceSourceService: WorkspaceSourceService) {
      this.rootUri = params.rootUri
      this.initializeParams = params

      initialized = true

//      if (ready) {
      initializeCompilerService(workspaceSourceService)
//      }
   }

   private fun initializeCompilerService(workspaceSourceService: WorkspaceSourceService) {
      try {
         this.compilerService.initialize(workspaceSourceService)
      } catch (e: MalformedTaxiConfFileException) {
         // We've already logged the errors to the client, but can't compile.
         return
      }
      compile()
      publishDiagnosticMessages()
   }

   private fun logCompilationResult(result: DiagnosticMessagesWrapper) {
      client.logMessage(
         MessageParams(
            MessageType.Info,
            "Compiled ${result.countOfSources} sources in ${result.duration.toMillis()}ms"
         )
      )
   }
}

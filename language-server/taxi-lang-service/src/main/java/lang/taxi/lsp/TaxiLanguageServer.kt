package lang.taxi.lsp

import ch.qos.logback.classic.Level
import lang.taxi.CompilerConfig
import lang.taxi.lsp.logging.LspClientLogAppender
import lang.taxi.lsp.sourceService.FileBasedWorkspaceSourceService
import lang.taxi.lsp.sourceService.WorkspaceSourceServiceFactory
import lang.taxi.utils.log
import org.eclipse.lsp4j.CompletionOptions
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.SaveOptions
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.SignatureHelpOptions
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.TextDocumentSyncOptions
import org.eclipse.lsp4j.WorkspaceFoldersOptions
import org.eclipse.lsp4j.WorkspaceServerCapabilities
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import reactor.core.publisher.Mono
import java.util.concurrent.CompletableFuture
import kotlin.system.exitProcess


class TaxiLanguageServer(
   private val compilerConfig: CompilerConfig = CompilerConfig(),
   private val compilerService: TaxiCompilerService = TaxiCompilerService(compilerConfig),
   private val textDocumentService: TaxiTextDocumentService = TaxiTextDocumentService(compilerService),
   private val workspaceService: TaxiWorkspaceService = TaxiWorkspaceService(compilerService),
   private val lifecycleHandler: LanguageServerLifecycleHandler = NoOpLifecycleHandler,
   private val workspaceSourceServiceFactory: WorkspaceSourceServiceFactory = FileBasedWorkspaceSourceService.Companion.Factory()
) : LanguageServer, LanguageClientAware {

   private lateinit var client: LanguageClient

   override fun shutdown(): CompletableFuture<Any>? {
      // shutdown request from the client, so exit gracefully
      terminate(0)
      return null
   }

   override fun getTextDocumentService(): TextDocumentService {
      return textDocumentService
   }

   override fun exit() {
      terminate(0)
   }

   private fun terminate(exitCode: Int) {
      lifecycleHandler.terminate(exitCode)
   }

   fun forceReloadOfSources(reason: String) {
      textDocumentService.forceReload(reason)
   }

   override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
      return Mono.defer {
         workspaceService.initialize(params)
         val workspaceSourceService = workspaceSourceServiceFactory.build(params, client)
         textDocumentService.initialize(params, workspaceSourceService)
         // Copied from:
         // https://github.com/NipunaMarcus/hellols/blob/master/language-server/src/main/java/org/hello/ls/langserver/HelloLanguageServer.java

         // Initialize the InitializeResult for this LS.
         val initializeResult = InitializeResult(ServerCapabilities())

         // Set the capabilities of the LS to inform the client.
         val capabilities = initializeResult.capabilities
         capabilities.setTextDocumentSync(TextDocumentSyncOptions().apply {
            change = TextDocumentSyncKind.Full
            save = Either.forRight(SaveOptions(false))
         })
         capabilities.definitionProvider = Either.forLeft(true)
         capabilities.workspaceSymbolProvider = Either.forLeft(true)
         capabilities.hoverProvider = Either.forLeft(true)
         capabilities.documentFormattingProvider = Either.forLeft(true)
         capabilities.signatureHelpProvider = SignatureHelpOptions()
         capabilities.setCodeActionProvider(true)
         capabilities.workspace = WorkspaceServerCapabilities(WorkspaceFoldersOptions().apply {
            supported = true
            setChangeNotifications(true)
         })
         val completionOptions = CompletionOptions()
         capabilities.completionProvider = completionOptions
         Mono.just(initializeResult)
      }.toFuture()

   }

   override fun getWorkspaceService(): WorkspaceService {
      return workspaceService
   }

   override fun connect(client: LanguageClient) {
      this.client = client
      listOf(this.textDocumentService, this.workspaceService)
         .filterIsInstance<LanguageClientAware>()
         .forEach { it.connect(client) }

      configureLoggers(client)
      client.logMessage(
         MessageParams(
            MessageType.Info, "Taxi Language Server Connected"
         )
      )
   }

   private fun configureLoggers(client: LanguageClient) {
//      try {
//         val config = client.configuration(ConfigurationParams(
//            listOf(ConfigurationItem().apply { section = "taxi" })
//         )).get(1, TimeUnit.SECONDS)
//      } catch (e:Exception) {
//         println("Oops.")
//      }
      // TODO : Can we use a ConfigurationRequest to set loggers and log levels?
      LspClientLogAppender.installFor(
         client, loggers = listOf(
            // Compiler etc:
            "lang.taxi" to Level.INFO,
            // LSP:
            "lang.taxi.lsp" to Level.DEBUG,
            // Package Manager etc:
            "org.taxilang" to Level.DEBUG,
            // Maven base classes for Package Manager
            "org.eclipse.aether" to Level.DEBUG
         )
      )
   }

}

interface LanguageServerLifecycleHandler {
   fun terminate(exitCode: Int) {
      log().debug("Ignoring request to terminate with exit code $exitCode")
   }
}

object NoOpLifecycleHandler : LanguageServerLifecycleHandler
object ProcessLifecycleHandler : LanguageServerLifecycleHandler {
   override fun terminate(exitCode: Int) {
      exitProcess(exitCode)
   }
}

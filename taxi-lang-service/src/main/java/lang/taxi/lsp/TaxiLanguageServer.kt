package lang.taxi.lsp

import lang.taxi.CompilerConfig
import lang.taxi.utils.log
import org.eclipse.lsp4j.CompletionOptions
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.SaveOptions
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.TextDocumentSyncOptions
import org.eclipse.lsp4j.WorkspaceFoldersOptions
import org.eclipse.lsp4j.WorkspaceServerCapabilities
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.util.concurrent.CompletableFuture
import kotlin.system.exitProcess


class TaxiLanguageServer(
        private val compilerConfig:CompilerConfig = CompilerConfig(),
        private val compilerService: TaxiCompilerService = TaxiCompilerService(compilerConfig),
        private val textDocumentService: TaxiTextDocumentService = TaxiTextDocumentService(compilerService),
        private val workspaceService: TaxiWorkspaceService = TaxiWorkspaceService(compilerService),
        private val lifecycleHandler: LanguageServerLifecycleHandler = NoOpLifecycleHandler
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

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
        workspaceService.initialize(params)
        textDocumentService.initialize(params)
        // Copied from:
        // https://github.com/NipunaMarcus/hellols/blob/master/language-server/src/main/java/org/hello/ls/langserver/HelloLanguageServer.java

        // Initialize the InitializeResult for this LS.
        val initializeResult = InitializeResult(ServerCapabilities())

        // Set the capabilities of the LS to inform the client.
        val capabilities = initializeResult.capabilities
        capabilities.setTextDocumentSync(TextDocumentSyncOptions().apply {
            change = TextDocumentSyncKind.Full
            save = SaveOptions(false)
        })
        capabilities.definitionProvider  = true
        capabilities.workspaceSymbolProvider = true
        capabilities.hoverProvider = true
        capabilities.documentFormattingProvider = true
        capabilities.setCodeActionProvider(true)
        capabilities.workspace = WorkspaceServerCapabilities(WorkspaceFoldersOptions().apply {
            supported = true
            setChangeNotifications(true)
        })
        val completionOptions = CompletionOptions()
        capabilities.completionProvider = completionOptions
        return CompletableFuture.supplyAsync { initializeResult }
    }

    override fun getWorkspaceService(): WorkspaceService {
        return workspaceService
    }

    override fun connect(client: LanguageClient) {
        this.client = client
        listOf(this.textDocumentService, this.workspaceService)
                .filterIsInstance<LanguageClientAware>()
                .forEach { it.connect(client) }
        client.logMessage(MessageParams(
                MessageType.Info, "Taxi Language Server Connected"
        ))
    }

}

interface LanguageServerLifecycleHandler {
    fun terminate(exitCode: Int) {
        log().warn("Ignoring request to terminate with exit code $exitCode")
    }
}

object NoOpLifecycleHandler : LanguageServerLifecycleHandler
object ProcessLifecycleHandler : LanguageServerLifecycleHandler {
    override fun terminate(exitCode: Int) {
        exitProcess(exitCode)
    }
}
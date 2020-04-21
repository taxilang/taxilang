package lang.taxi.lsp

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.*
import java.util.concurrent.CompletableFuture
import kotlin.system.exitProcess


class TaxiLanguageServer(
        private val workspaceService: WorkspaceService = TaxiWorkspaceService(),
        private val textDocumentService: TextDocumentService = TaxiTextDocumentService()

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
        exitProcess(exitCode)
    }

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
        // Copied from:
        // https://github.com/NipunaMarcus/hellols/blob/master/language-server/src/main/java/org/hello/ls/langserver/HelloLanguageServer.java

        // Initialize the InitializeResult for this LS.
        val initializeResult = InitializeResult(ServerCapabilities())

        // Set the capabilities of the LS to inform the client.
        initializeResult.capabilities.setTextDocumentSync(TextDocumentSyncKind.Full)
        val completionOptions = CompletionOptions()
        initializeResult.capabilities.completionProvider = completionOptions
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
    }

}
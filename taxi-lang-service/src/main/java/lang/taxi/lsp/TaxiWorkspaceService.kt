package lang.taxi.lsp

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.WorkspaceService

class TaxiWorkspaceService : WorkspaceService, LanguageClientAware {
    private lateinit var client: LanguageClient
    val receivedEvents = mutableListOf<Any>()
    var workspaceFolders:List<WorkspaceFolder> = emptyList()
        private set

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
        receivedEvents.add(params)
    }

    override fun didChangeConfiguration(params: DidChangeConfigurationParams) {
        receivedEvents.add(params)
    }

    override fun didChangeWorkspaceFolders(params: DidChangeWorkspaceFoldersParams) {
        receivedEvents.add(params)
    }

    override fun connect(client: LanguageClient) {
        this.client = client
        client.workspaceFolders().handle { folders, throwable ->
            this.workspaceFolders = folders.toList()
        }
    }

}
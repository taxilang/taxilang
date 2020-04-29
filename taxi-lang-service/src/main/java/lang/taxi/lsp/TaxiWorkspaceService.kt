package lang.taxi.lsp

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.WorkspaceService

class TaxiWorkspaceService() : WorkspaceService {
    val receivedEvents = mutableListOf<Any>()

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
        receivedEvents.add(params)
    }

    override fun didChangeConfiguration(params: DidChangeConfigurationParams) {
        receivedEvents.add(params)
    }

    override fun didChangeWorkspaceFolders(params: DidChangeWorkspaceFoldersParams) {
        receivedEvents.add(params)
    }

    fun initialize(params: InitializeParams) {
    }

}
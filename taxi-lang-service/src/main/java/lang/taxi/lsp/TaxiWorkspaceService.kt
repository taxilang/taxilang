package lang.taxi.lsp

import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.WorkspaceService

class TaxiWorkspaceService : WorkspaceService, LanguageClientAware {
    private lateinit var client: LanguageClient
    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams?) {
    }

    override fun didChangeConfiguration(params: DidChangeConfigurationParams?) {
    }

    override fun connect(client: LanguageClient) {
        this.client = client
    }

}
package lang.taxi.lsp

import lang.taxi.lsp.notebook.*
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.util.concurrent.CompletableFuture

/**
 * Composite server that combines the standard Taxi language server
 * with notebook-specific functionality.
 *
 * This allows the JSONRPC launcher to route both standard LSP requests
 * and custom notebook requests to the appropriate handlers.
 */
class TaxiLanguageServerWithNotebooks(
   private val languageServer: TaxiLanguageServer,
   private val notebookService: NotebookService
) : LanguageServer, NotebookService, LanguageClientAware {

   // Delegate LanguageServer methods to the underlying language server
   override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
      return languageServer.initialize(params)
   }

   override fun shutdown(): CompletableFuture<Any>? {
      return languageServer.shutdown()
   }

   override fun exit() {
      languageServer.exit()
   }

   override fun getTextDocumentService(): TextDocumentService {
      return languageServer.textDocumentService
   }

   override fun getWorkspaceService(): WorkspaceService {
      return languageServer.workspaceService
   }

   override fun connect(client: LanguageClient) {
      languageServer.connect(client)
   }

   override fun generateQueryPlan(params: StubQueryRequest): CompletableFuture<QueryPlanResponse> {
      return notebookService.generateQueryPlan(params)
   }

   // Delegate NotebookService methods to the notebook service
   override fun executeWithStubs(params: StubQueryRequest): CompletableFuture<StubQueryResponse> {
      return notebookService.executeWithStubs(params)
   }

   override fun listOperations(params: ListOperationsRequest): CompletableFuture<ListOperationsResponse> {
      return notebookService.listOperations(params)
   }
}

package lang.taxi.lsp

import lang.taxi.lsp.gotoDefinition.toLocation
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.WorkspaceSymbolParams
import org.eclipse.lsp4j.services.WorkspaceService
import java.util.concurrent.CompletableFuture

class TaxiWorkspaceService(private val compilerService: TaxiCompilerService) : WorkspaceService {
    val receivedEvents = mutableListOf<Any>()

    override fun symbol(params: WorkspaceSymbolParams): CompletableFuture<MutableList<out SymbolInformation>> {
       val compiler = this.compilerService.lastCompilation()?.compiler
          ?: return CompletableFuture.completedFuture(mutableListOf())
       val typeNames = compiler.declaredTypeNames()
       val symbols = typeNames.filter { it.fullyQualifiedName.contains(params.query, ignoreCase = true) }
          .mapNotNull { qualifiedName ->
             val compilationUnit = compiler.getDeclarationSource(qualifiedName)
             compilationUnit?.let { compilationUnit ->
                SymbolInformation(
                   qualifiedName.fullyQualifiedName,
                   SymbolKind.Class,
                   compilationUnit.toLocation()
                )
                    }
                }
        return CompletableFuture.completedFuture(symbols.toMutableList())
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
        // Reload sources if a new file was added or deleted.  Changes are handled in the compiler service
        val reloadSources = params.changes.any { fileEvent ->
            fileEvent.type == FileChangeType.Created || fileEvent.type == FileChangeType.Deleted
        }
        if (reloadSources) {
            compilerService.reloadSourcesAndTriggerCompilation()
        }
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

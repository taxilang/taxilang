package lang.taxi.lsp

import lang.taxi.lsp.gotoDefinition.toLocation
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.WorkspaceService
import java.util.concurrent.CompletableFuture

class TaxiWorkspaceService(private val compilerService: TaxiCompilerService) : WorkspaceService {
    val receivedEvents = mutableListOf<Any>()

    override fun symbol(params: WorkspaceSymbolParams): CompletableFuture<MutableList<out SymbolInformation>> {
        val compiler = this.compilerService.lastCompilationResult.get().compiler
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
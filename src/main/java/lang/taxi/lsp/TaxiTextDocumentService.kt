package lang.taxi.lsp

import lang.taxi.CompilationException
import lang.taxi.Compiler
import lang.taxi.TaxiDocument
import lang.taxi.lsp.completion.TypeProvider
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.TextDocumentService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference


class TaxiTextDocumentService : TextDocumentService, LanguageClientAware {
    private val masterDocument: AtomicReference<TaxiDocument> = AtomicReference();
    private val compiledDocuments: MutableMap<String, TaxiDocument> = mutableMapOf()
    private val typeProvider = TypeProvider(masterDocument)
    private lateinit var client: LanguageClient
    override fun completion(position: CompletionParams?): CompletableFuture<Either<MutableList<CompletionItem>, CompletionList>> {

        // Provide completion item.
        return CompletableFuture.supplyAsync<Either<MutableList<CompletionItem>, CompletionList>> {
            Either.forLeft(typeProvider.getTypes().toMutableList())
        }
    }

    override fun didOpen(params: DidOpenTextDocumentParams?) {
    }

    override fun didSave(params: DidSaveTextDocumentParams?) {
    }

    override fun didClose(params: DidCloseTextDocumentParams?) {
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        if (params.contentChanges.size > 1) {
            error("Multiple changes not supported yet")
        }
        val change = params.contentChanges.first()
        if (change.range != null) {
            TODO("Ranged changes not yet supported")
        }
        val content = change.text
        val sourceName = params.textDocument.uri
        val uri = sourceName
        try {
            val importSources = compiledDocuments.filterKeys { it != sourceName }
                    .values.toList()
            val compiled = Compiler(content, sourceName, importSources).compile()
            compiledDocuments[sourceName] = compiled
            masterDocument.set(compiled)
            client.publishDiagnostics(PublishDiagnosticsParams(sourceName, emptyList(), params.textDocument.version))
        } catch (e: CompilationException) {
            val diagnostics = e.errors.map { error ->
                // Note - for VSCode, we can use the same position for start and end, and it
                // highlights the entire word
                val position = Position(
                        error.offendingToken.line,
                        error.offendingToken.charPositionInLine
                )
                Diagnostic(
                        Range(position, position),
                        error.detailMessage,
                        DiagnosticSeverity.Error,
                        "Compiler"
                )
            }
            client.publishDiagnostics(PublishDiagnosticsParams(
                    sourceName,
                    diagnostics,
                    params.textDocument.version
            ))
        }


    }

    override fun connect(client: LanguageClient) {
        this.client = client
    }

}
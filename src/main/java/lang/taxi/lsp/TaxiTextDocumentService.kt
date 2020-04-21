package lang.taxi.lsp

import lang.taxi.CompilationException
import lang.taxi.Compiler
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.TextDocumentService
import java.util.concurrent.CompletableFuture


class TaxiTextDocumentService : TextDocumentService, LanguageClientAware {
    private lateinit var client: LanguageClient
    override fun completion(position: CompletionParams?): CompletableFuture<Either<MutableList<CompletionItem>, CompletionList>> {
        // Provide completion item.
        // Provide completion item.
        return CompletableFuture.supplyAsync<Either<MutableList<CompletionItem>, CompletionList>> {
            val completionItems: MutableList<CompletionItem> = ArrayList()
            try {
                val p = position
                // Sample Completion item for sayHello
                val completionItem = CompletionItem()
                // Define the text to be inserted in to the file if the completion item is selected.
                completionItem.insertText = "sayHello() {\n    print(\"hello\")\n}"
                // Set the label that shows when the completion drop down appears in the Editor.
                completionItem.label = "sayHello()"
                // Set the completion kind. This is a snippet.
                // That means it replace character which trigger the completion and
                // replace it with what defined in inserted text.
                completionItem.kind = CompletionItemKind.Snippet
                // This will set the details for the snippet code which will help user to
                // understand what this completion item is.
                completionItem.detail = "sayHello()\n this will say hello to the people"

                // Add the sample completion item to the list.
                completionItems.add(completionItem)
            } catch (e: Exception) {
                //TODO: Handle the exception.
            }
            Either.forLeft(completionItems)
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
        val uri = params.textDocument.uri
        try {
            Compiler(content, params.textDocument.uri).compile()
            client.publishDiagnostics(PublishDiagnosticsParams(params.textDocument.uri, emptyList(), params.textDocument.version))
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
                    params.textDocument.uri,
                    diagnostics,
                    params.textDocument.version
            ))
        }


    }

    override fun connect(client: LanguageClient) {
        this.client = client
    }

}
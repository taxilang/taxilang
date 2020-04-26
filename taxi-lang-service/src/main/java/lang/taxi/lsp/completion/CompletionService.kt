package lang.taxi.lsp.completion

import lang.taxi.TaxiParser
import lang.taxi.lsp.CompiledFile
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.util.concurrent.CompletableFuture

class CompletionService(private val typeProvider: TypeProvider) {
    fun computeCompletions(file: CompiledFile, params: CompletionParams): CompletableFuture<Either<MutableList<CompletionItem>, CompletionList>> {
        val context = file.compiler.contextAt(params.position.line, params.position.character)
                ?: return completions(TopLevelCompletions.topLevelCompletionItems)

        val completionItems = when (context.ruleContext.ruleIndex) {
            TaxiParser.RULE_fieldDeclaration -> typeProvider.getTypes()
            else -> emptyList()
        }
        return completions(completionItems)
    }
}

fun completions(list: List<CompletionItem> = emptyList()): CompletableFuture<Either<MutableList<CompletionItem>, CompletionList>> {
    return CompletableFuture.completedFuture(Either.forLeft(list.toMutableList()))
}

fun markdown(content: String): MarkupContent {
    return MarkupContent("markdown", content)
}
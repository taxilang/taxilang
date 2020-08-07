package lang.taxi.lsp.completion

import lang.taxi.Compiler
import lang.taxi.TaxiParser
import lang.taxi.lsp.CompilationResult
import lang.taxi.types.QualifiedName
import lang.taxi.types.Type
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.net.URI
import java.util.concurrent.CompletableFuture

class CompletionService(private val typeProvider: TypeProvider) {
    fun computeCompletions(compilationResult: CompilationResult, params: CompletionParams): CompletableFuture<Either<MutableList<CompletionItem>, CompletionList>> {
        val context = compilationResult.compiler.contextAt(params.position.line, params.position.character, params.textDocument.uri)
                ?: return completions(TopLevelCompletions.topLevelCompletionItems)

        val importDecorator = ImportCompletionDecorator(compilationResult.compiler, params.textDocument.uri)
        val completionItems = when (context.ruleIndex) {
            TaxiParser.RULE_fieldDeclaration -> typeProvider.getTypes(listOf(importDecorator))
            TaxiParser.RULE_typeMemberDeclaration -> typeProvider.getTypes(listOf(importDecorator))
            TaxiParser.RULE_listOfInheritedTypes -> typeProvider.getTypes(listOf(importDecorator))
            // This next one feels wrong, but it's what I'm seeing debugging.
            // suspect our matching of token to cursor position might be off
            TaxiParser.RULE_typeType -> typeProvider.getTypes(listOf(importDecorator))
            TaxiParser.RULE_caseScalarAssigningDeclaration -> typeProvider.getEnumValues(listOf(importDecorator), context.start.text)
            TaxiParser.RULE_enumSynonymSingleDeclaration -> typeProvider.getEnumValues(listOf(importDecorator), context.start.text)
            else -> emptyList()
        }
        return completions(completionItems)
    }
}

private class ImportCompletionDecorator(compiler: Compiler, sourceUri: String) : CompletionDecorator {
    val typesDeclaredInFile = compiler.typeNamesForSource(sourceUri)
    val importsDeclaredInFile = compiler.importedTypesInSource(sourceUri)

    override fun decorate(typeName: QualifiedName, type: Type?, completionItem: CompletionItem): CompletionItem {
        // TODO : Insert after other imports
        val insertPosition = Range(
                Position(0, 0),
                Position(0, 0)
        )
        if (completionItem.additionalTextEdits == null) {
            completionItem.additionalTextEdits = mutableListOf()
        }
        if (!typesDeclaredInFile.contains(typeName) && !importsDeclaredInFile.contains(typeName)) {
            completionItem.additionalTextEdits.add(TextEdit(
                    insertPosition,
                    "import $typeName\n"
            ))
        }
        return completionItem
    }

}

fun completions(list: List<CompletionItem> = emptyList()): CompletableFuture<Either<MutableList<CompletionItem>, CompletionList>> {
    return CompletableFuture.completedFuture(Either.forLeft(list.toMutableList()))
}

fun markdown(content: String): MarkupContent {
    return MarkupContent("markdown", content)
}

fun TextDocumentIdentifier.uriPath(): String {
    return URI.create(this.uri).path
}
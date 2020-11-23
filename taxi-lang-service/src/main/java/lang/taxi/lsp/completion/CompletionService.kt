package lang.taxi.lsp.completion

import lang.taxi.Compiler
import lang.taxi.TaxiParser
import lang.taxi.lsp.CompilationResult
import lang.taxi.types.QualifiedName
import lang.taxi.types.SourceNames
import lang.taxi.types.Type
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.net.URI
import java.util.concurrent.CompletableFuture

class CompletionService(private val typeProvider: TypeProvider) {
    fun computeCompletions(compilationResult: CompilationResult, params: CompletionParams): CompletableFuture<Either<MutableList<CompletionItem>, CompletionList>> {
        val context = compilationResult.compiler.contextAt(params.position.line, params.position.character, params.textDocument.uriPath())
                ?: return completions(TopLevelCompletions.topLevelCompletionItems)

        val importDecorator = ImportCompletionDecorator(compilationResult.compiler, params.textDocument.uriPath())
        val completionItems = when (context.ruleIndex) {
            TaxiParser.RULE_columnIndex -> buildColumnIndexSuggestions()
            TaxiParser.RULE_fieldDeclaration -> typeProvider.getTypes(listOf(importDecorator))
            TaxiParser.RULE_typeMemberDeclaration -> typeProvider.getTypes(listOf(importDecorator))
            TaxiParser.RULE_listOfInheritedTypes -> typeProvider.getTypes(listOf(importDecorator))
            // This next one feels wrong, but it's what I'm seeing debugging.
            // suspect our matching of token to cursor position might be off
            TaxiParser.RULE_typeType -> typeProvider.getTypes(listOf(importDecorator))
            TaxiParser.RULE_caseScalarAssigningDeclaration -> typeProvider.getEnumValues(listOf(importDecorator), context.start.text)
            TaxiParser.RULE_enumSynonymSingleDeclaration -> provideEnumCompletions(context.start.text, listOf(importDecorator))
            TaxiParser.RULE_enumSynonymDeclaration -> provideEnumCompletions(context.start.text, listOf(importDecorator))
            TaxiParser.RULE_enumConstants -> listOf(CompletionItem("synonym of"))
            else -> emptyList()
        }
        return completions(completionItems)
    }

    private fun buildColumnIndexSuggestions(): List<CompletionItem> {
        return listOf(
                CompletionItem("Column index").apply {
                    insertText = "1"
                    insertTextFormat = InsertTextFormat.Snippet
                    documentation = Either.forRight(MarkupContent(
                            "markdown",
                            """Sets the column number to read this attribute from.  Columns are numbered starting at 1.
                                    |
                                    |eg:
                                    |
                                    |```
                                    |model Person {
                                    |   firstName : FirstName by column(1)
                                    |}
                                    |```
                                """.trimMargin()
                    ))
                },
                CompletionItem("Column name").apply {
                    insertText = "\"$0\""
                    insertTextFormat = InsertTextFormat.Snippet
                    documentation = Either.forRight(MarkupContent(
                            "markdown",
                            """Sets the column name to read this attribute from.  Column names are generally read from the first row.
                                    |
                                    |eg:
                                    |
                                    |```
                                    |model Person {
                                    |   firstName : FirstName by column("First Name")
                                    |}
                                    |```
                                """.trimMargin()
                    ))
                }

        )
    }

    /**
     * If the user hasn't typed a full enum name yet, returns a list of enum names.
     * Otherwise, returns values within the enum
     */
    private fun provideEnumCompletions(text: String?, decorators: List<CompletionDecorator>): List<CompletionItem> {
        if (text == null || text.isEmpty()) {
            return emptyList()
        }

        val enumTypeName = typeProvider.getTypeName(text)
        return if (enumTypeName == null) {
            // Haven't picked an enum yet, so lets offer the available enums
            typeProvider.getEnumTypes(decorators)
        } else {
            typeProvider.getEnumValues(decorators, enumTypeName)
        }
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

fun TextDocumentIdentifier.normalizedUriPath(): String {
    return SourceNames.normalize(this.uri)
}
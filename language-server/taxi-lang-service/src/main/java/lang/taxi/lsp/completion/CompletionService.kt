package lang.taxi.lsp.completion

import lang.taxi.Compiler
import lang.taxi.TaxiParser
import lang.taxi.lsp.CompilationResult
import lang.taxi.types.QualifiedName
import lang.taxi.types.SourceNames
import lang.taxi.types.Type
import org.antlr.v4.runtime.ParserRuleContext
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.net.URI
import java.util.concurrent.CompletableFuture

class CompletionService(private val typeProvider: TypeProvider) {
   fun computeCompletions(
      compilationResult: CompilationResult,
      params: CompletionParams
   ): CompletableFuture<Either<MutableList<CompletionItem>, CompletionList>> {
      val normalizedUriPath = params.textDocument.normalizedUriPath()
      val importDecorator = ImportCompletionDecorator(compilationResult.compiler, normalizedUriPath)

      val zeroBasedLineIndex = params.position.line
      val char = params.position.character
      // contextAt() is the most specific.  If we match an exact token, it'll be returned.
      val context = compilationResult.compiler.contextAt(zeroBasedLineIndex, char, normalizedUriPath)
         ?:
         // getNearestToken() returns the token if we're not on an exact match location, but could find a nearby one.
         compilationResult.compiler.getNearestToken(
            zeroBasedLineIndex,
            char,
            normalizedUriPath
         ) as? ParserRuleContext
         ?: return bestGuessCompletionsWithoutContext(compilationResult, params, importDecorator)

      val completionItems = getCompletionsForContext(context, importDecorator)
      return completions(completionItems)
   }

   private fun getCompletionsForContext(
      context: ParserRuleContext,
      importDecorator: ImportCompletionDecorator
   ): List<CompletionItem> {
      val completionItems = when (context.ruleIndex) {
         TaxiParser.RULE_columnIndex -> buildColumnIndexSuggestions()
         TaxiParser.RULE_simpleFieldDeclaration -> typeProvider.getTypes(listOf(importDecorator))
         TaxiParser.RULE_fieldDeclaration -> typeProvider.getTypes(listOf(importDecorator))
         TaxiParser.RULE_typeMemberDeclaration -> typeProvider.getTypes(listOf(importDecorator))
         TaxiParser.RULE_listOfInheritedTypes -> typeProvider.getTypes(listOf(importDecorator))
         // This next one feels wrong, but it's what I'm seeing debugging.
         // suspect our matching of token to cursor position might be off
         TaxiParser.RULE_typeType -> typeProvider.getTypes(listOf(importDecorator))
         TaxiParser.RULE_caseScalarAssigningDeclaration -> typeProvider.getEnumValues(
            listOf(importDecorator),
            context.start.text
         )
         TaxiParser.RULE_enumSynonymSingleDeclaration -> provideEnumCompletions(
            context.start.text,
            listOf(importDecorator)
         )
         TaxiParser.RULE_enumSynonymDeclaration -> provideEnumCompletions(context.start.text, listOf(importDecorator))
         TaxiParser.RULE_enumConstants -> listOf(CompletionItem("synonym of"))

         // Query completions
         TaxiParser.RULE_parameterConstraintExpression -> typeProvider.getTypes(listOf(importDecorator))
         TaxiParser.RULE_queryTypeList -> typeProvider.getTypes(listOf(importDecorator))
         TaxiParser.RULE_listType -> typeProvider.getTypes(listOf(importDecorator))
         TaxiParser.RULE_parameterConstraint -> typeProvider.getTypes(listOf(importDecorator))
         else -> {
            when {
               context is TaxiParser.TemporalFormatListContext && context.text.isEmpty() -> {
                  // We can hit this when doing completions in a query:
                  // findAll { Person( <--- here.
                  // The grammar will match as a TemportalFormatList, but it could equally
                  // be a place for defining constraint types.
                  // If there's no text yet, then hop up to the parent node, and try again.
                  getCompletionsForContext(context.parent as ParserRuleContext, importDecorator)
               }
               else -> emptyList()
            }

         }
      }
      return completionItems
   }

   /**
    * We weren't able to resolve the context at the provided location.
    * This could be there's no text there, or the text doesn't compile yet.
    * Therefore, look around, and try to guess what the user is doing.
    */
   private fun bestGuessCompletionsWithoutContext(
      compilationResult: CompilationResult,
      params: CompletionParams,
      importDecorator: ImportCompletionDecorator
   ): CompletableFuture<Either<MutableList<CompletionItem>, CompletionList>> {
      val lookupResult = compilationResult.compiler.getNearestToken(
         params.position.line,
         params.position.character,
         params.textDocument.normalizedUriPath()
      )
      return when {
         isIncompleteFieldDefinition(lookupResult) -> completions(typeProvider.getTypes(listOf(importDecorator)))
         else -> completions(TopLevelCompletions.topLevelCompletionItems)
      }
   }

   private fun buildColumnIndexSuggestions(): List<CompletionItem> {
      return listOf(
         CompletionItem("Column index").apply {
            insertText = "1"
            insertTextFormat = InsertTextFormat.Snippet
            documentation = Either.forRight(
               MarkupContent(
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
               )
            )
         },
         CompletionItem("Column name").apply {
            insertText = "\"$0\""
            insertTextFormat = InsertTextFormat.Snippet
            documentation = Either.forRight(
               MarkupContent(
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
               )
            )
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
         completionItem.additionalTextEdits.add(
            TextEdit(
               insertPosition,
               "import $typeName\n"
            )
         )
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

package lang.taxi.lsp.completion

import lang.taxi.TaxiParser
import lang.taxi.lsp.CompilationResult
import lang.taxi.types.SourceNames
import org.antlr.v4.runtime.ParserRuleContext
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.net.URI
import java.util.concurrent.CompletableFuture

/**
 * A completion service which aims to give hints when editing models and types.
 * Not focussed on querying
 */
class EditorCompletionService(private val typeProvider: TypeProvider) : CompletionProvider, CompletionService {
   override fun computeCompletions(
      compilationResult: CompilationResult,
      params: CompletionParams
   ): CompletableFuture<Either<MutableList<CompletionItem>, CompletionList>> {
      val (importDecorator, context) = CompletionService.buildCompletionParams(params, compilationResult)

      val completionItems = getCompletionsForContext(
         compilationResult,
         params,
         importDecorator,
         context
      )
      return completionItems.thenApply {
         Either.forLeft(it.toMutableList())
      }
   }

   override fun getCompletionsForContext(
      compilationResult: CompilationResult,
      params: CompletionParams,
      importDecorator: ImportCompletionDecorator,
      contextAtCursor: ParserRuleContext?
   ): CompletableFuture<List<CompletionItem>> {
      if (contextAtCursor == null) {
         return bestGuessCompletionsWithoutContext(compilationResult, params, importDecorator)
      }

      val completionItems = getCompletionsForContext(contextAtCursor, importDecorator)
      return completed(completionItems)
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
   ): CompletableFuture<List<CompletionItem>> {
      val lookupResult = compilationResult.compiler.getNearestToken(
         params.position.line,
         params.position.character,
         params.textDocument.normalizedUriPath()
      )
      return when {
         isIncompleteFieldDefinition(lookupResult) -> completed(typeProvider.getTypes(listOf(importDecorator)))
         else -> completed(TopLevelCompletions.topLevelCompletionItems)
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

fun completed(list: List<CompletionItem> = emptyList()) = CompletableFuture.completedFuture(list)
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

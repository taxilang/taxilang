package lang.taxi.lsp.completion

import lang.taxi.TaxiParser
import lang.taxi.TaxiParser.CaseScalarAssigningDeclarationContext
import lang.taxi.TaxiParser.ColumnIndexContext
import lang.taxi.TaxiParser.EnumConstantContext
import lang.taxi.TaxiParser.EnumSynonymDeclarationContext
import lang.taxi.TaxiParser.EnumSynonymSingleDeclarationContext
import lang.taxi.TaxiParser.ExpressionAtomContext
import lang.taxi.TaxiParser.FieldDeclarationContext
import lang.taxi.TaxiParser.IdentifierContext
import lang.taxi.TaxiParser.ListOfInheritedTypesContext
import lang.taxi.TaxiParser.ListTypeContext
import lang.taxi.TaxiParser.ParameterConstraintContext
import lang.taxi.TaxiParser.ParameterConstraintExpressionContext
import lang.taxi.TaxiParser.QueryTypeListContext
import lang.taxi.TaxiParser.SimpleFieldDeclarationContext
import lang.taxi.TaxiParser.TypeMemberDeclarationContext
import lang.taxi.TaxiParser.TypeTypeContext
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
      params: CompletionParams,
      lastSuccessfulCompilation: CompilationResult?
   ): CompletableFuture<Either<MutableList<CompletionItem>, CompletionList>> {
      val (importDecorator, context) = CompletionService.buildCompletionParams(params, compilationResult)

      val completionItems = getCompletionsForContext(
         compilationResult,
         params,
         importDecorator,
         context,
         lastSuccessfulCompilation
      )
      return completionItems.thenApply {
         Either.forLeft(it.toMutableList())
      }
   }

   override fun getCompletionsForContext(
      compilationResult: CompilationResult,
      params: CompletionParams,
      importDecorator: ImportCompletionDecorator,
      contextAtCursor: ParserRuleContext?,
      lastSuccessfulCompilation: CompilationResult?,
   ): CompletableFuture<List<CompletionItem>> {
      if (contextAtCursor == null) {
         return bestGuessCompletionsWithoutContext(compilationResult, params, importDecorator)
      }

      val completionItems =
         getCompletionsForContext(contextAtCursor, importDecorator, compilationResult, lastSuccessfulCompilation)
      return completed(completionItems)
   }

   private fun getCompletionsForContext(
      context: ParserRuleContext,
      importDecorator: ImportCompletionDecorator,
      compilationResult: CompilationResult,
      lastSuccessfulCompilation: CompilationResult?,
   ): List<CompletionItem> {


      val completionContext = when {
         // IdentifierContext is generally too general purpose to offer any insights.
         // Go higher.
         context is IdentifierContext -> context.parent as ParserRuleContext
         else -> context
      }
      val completionItems = when (completionContext) {
         is ColumnIndexContext -> buildColumnIndexSuggestions()
         is SimpleFieldDeclarationContext -> typeProvider.getTypes(listOf(importDecorator))
         is FieldDeclarationContext -> typeProvider.getTypes(listOf(importDecorator))
         is TypeMemberDeclarationContext -> typeProvider.getTypes(listOf(importDecorator))
         is ListOfInheritedTypesContext -> typeProvider.getTypes(listOf(importDecorator))
         is ExpressionAtomContext -> calculateExpressionSuggestions(
            context as ExpressionAtomContext,
            importDecorator,
            compilationResult,
            lastSuccessfulCompilation
         )
         // This next one feels wrong, but it's what I'm seeing debugging.
         // suspect our matching of token to cursor position might be off
         is TypeTypeContext -> typeProvider.getTypes(listOf(importDecorator))
         is CaseScalarAssigningDeclarationContext -> typeProvider.getEnumValues(
            listOf(importDecorator),
            context.start.text
         )

         is EnumSynonymSingleDeclarationContext -> provideEnumCompletions(
            context.start.text,
            listOf(importDecorator)
         )

         is EnumSynonymDeclarationContext -> provideEnumCompletions(context.start.text, listOf(importDecorator))
         is EnumConstantContext -> listOf(CompletionItem("synonym of"))

         // Query completions
         is ParameterConstraintExpressionContext -> typeProvider.getTypes(listOf(importDecorator))
         is QueryTypeListContext -> typeProvider.getTypes(listOf(importDecorator))
         is ListTypeContext -> typeProvider.getTypes(listOf(importDecorator))
         is ParameterConstraintContext -> typeProvider.getTypes(listOf(importDecorator))
         else -> {
            when {
               context is TaxiParser.TemporalFormatListContext && context.text.isEmpty() -> {
                  // We can hit this when doing completions in a query:
                  // findAll { Person( <--- here.
                  // The grammar will match as a TemportalFormatList, but it could equally
                  // be a place for defining constraint types.
                  // If there's no text yet, then hop up to the parent node, and try again.
                  getCompletionsForContext(
                     context.parent as ParserRuleContext,
                     importDecorator,
                     compilationResult,
                     lastSuccessfulCompilation
                  )
               }

               else -> emptyList()
            }

         }
      }
      return completionItems
   }

   /**
    * Provides completions when the user is generating an expression.
    * Often, just a standard list of types, but in some cases (eg., enums),
    * we can offer better
    */
   private fun calculateExpressionSuggestions(
      context: ExpressionAtomContext,
      importDecorator: ImportCompletionDecorator,
      thisCompilationResult: CompilationResult,
      lastSuccessfulCompilation: CompilationResult?
   ): List<CompletionItem> {
      if (lastSuccessfulCompilation == null) {
         return emptyList()
      }
      // This should be easy, but it turns out, it isn't.
      // If the user is typing out a schema, we don't have complete tokens, so
      // we don't know that they're at an enum.
      // This used to be easier, because the expression grammar was more specific
      // (and actually buggier).
      // However, now that expressions are made up of general purpose atoms,
      // we don't get a specific AST until the block is complete. :(
      //
      // This blog post has some interested ideas about auto-genning suggestions
      // from the grammar:
      //
      // https://tomassetti.me/autocompletion-editor-antlr/
      //
      // It works by taking a look at the token at the cursor, and then working out
      // what possible tokens could follow.
      // It's a good idea, but the implementation has lots of corner cases that
      // need to be considered.
      //
      // I tried the linked implementation (not very hard), and didn't get great results - it simply
      // returned all the possible tokens, non those specific to our location.
      // Could still be worth investigation though, as it seems a better approach
      // than lots of hard-coded scenario specific suggestions.
      //
      // For now, we don't have a working solution, so just return an empty list.
      return emptyList()
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

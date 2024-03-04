package lang.taxi.lsp.completion

import lang.taxi.TaxiParser.*
import lang.taxi.expressions.Expression
import lang.taxi.expressions.TypeExpression
import lang.taxi.lsp.CompilationResult
import lang.taxi.searchUpExcluding
import lang.taxi.searchUpForRule
import lang.taxi.types.EnumType
import lang.taxi.types.PrimitiveType
import lang.taxi.types.SourceNames
import lang.taxi.types.Type
import org.antlr.v4.runtime.ParserRuleContext
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.net.URI
import java.util.concurrent.CompletableFuture

/**
 * A completion service which aims to give hints when editing models and types.
 * Not focussed on querying
 */
class EditorCompletionService(private val typeCompletionBuilder: TypeCompletionBuilder) : CompletionProvider, CompletionService {
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
      typeRepository: TypeRepository,
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
      val typeRepository = CompilationResultTypeRepository(lastSuccessfulCompilation, compilationResult)
      val completionContext = when {
         // IdentifierContext is generally too general purpose to offer any insights.
         // Go higher.
         context is IdentifierContext -> context.parent as ParserRuleContext
         else -> context
      }
      val decorators = listOf(importDecorator)
      val completionItems = when (completionContext) {
         is ExpressionAtomContext -> calculateExpressionSuggestions(
            context as ExpressionAtomContext,
            importDecorator,
            compilationResult,
            lastSuccessfulCompilation
         )
         is CaseScalarAssigningDeclarationContext -> typeCompletionBuilder.getEnumValues(
            typeRepository,
            decorators,
            context.start.text
         )

         is EnumSynonymSingleDeclarationContext -> provideEnumCompletions(
            typeRepository,
            context.start.text,
            decorators
         )

         is EnumSynonymDeclarationContext -> provideEnumCompletions(typeRepository, context.start.text, decorators)
         is EnumConstantContext -> listOf(CompletionItem("synonym of"))

         // Query completions
         is TypeExpressionContext -> typeCompletionBuilder.getTypes(typeRepository, decorators)
         is ArrayMarkerContext -> typeCompletionBuilder.getTypes(typeRepository, decorators)
         is ParameterConstraintContext -> typeCompletionBuilder.getTypes(typeRepository, decorators)
         else -> emptyList()
      }
      return completionItems
   }

   /**
    * Provides hints for the names of fields/properties
    * on an annotation
    *
    *  eg: @Foo( <---- caret is here
    */
   fun provideAnnotationFieldCompletions(
      contextAtCursor: ParserRuleContext,
      decorators: List<ImportCompletionDecorator>,
      compilationResult: CompilationResult
   ): List<CompletionItem> {
      val annotationCtx = contextAtCursor.searchUpForRule<AnnotationContext>()!!
      return compilationResult.compiler.lookupSymbolByName(
         annotationCtx.qualifiedName().text,
         annotationCtx.qualifiedName()
      ).map { annotationName ->
         val annotationType = compilationResult.document?.annotation(annotationName)
            ?: return emptyList()
         val completions = annotationType.fields.map { field ->
            val completionText = field.name + " = "
            CompletionItem(
               completionText
            ).apply {
               insertText = when (field.type.basePrimitive) {
                  PrimitiveType.STRING -> """${field.name} =  "$0" """
                  else -> completionText
               }
               insertTextFormat = InsertTextFormat.Snippet
               val optionalPrefix = if (field.nullable) {
                  "(Optional) "
               } else ""
               val defaultPrefix = if (field.accessor != null && field.accessor is Expression) {
                  val default = (field.accessor as Expression).asTaxi()
                  "Default: $default "
               } else ""
               detail = optionalPrefix + defaultPrefix + field.type.toQualifiedName().typeName

            }
         }
         completions
      }.getOrNull() ?: emptyList()
   }

   /**
    * Called when providing the values into an annotation field.
    * eg: @Foo(bar = <---- caret is here
    */
   fun provideAnnotationFieldValueCompletions(
      context: ElementValueContext,
      decorators: List<ImportCompletionDecorator>,
      compilationResult: CompilationResult
   ): List<CompletionItem> {
      val typeRepository = CompilationResultTypeRepository(compilationResult, null)
      val annotationContext = context.searchUpForRule<AnnotationContext>() ?: return emptyList()
      val annotationName = compilationResult.compiler.lookupSymbolByName(
         annotationContext.qualifiedName().text,
         annotationContext.qualifiedName()
      )
         .getOrNull() ?: return emptyList()
      if (compilationResult.document?.containsType(annotationName) != true) {
         return emptyList()
      }
      val annotation = compilationResult.document!!.annotation(annotationName)

      val fieldBeingDeclared =
         context.searchUpForRule<ElementValuePairContext>()?.identifier()?.text ?: return emptyList()
      val field = annotation.fields.firstOrNull { it.name == fieldBeingDeclared } ?: return emptyList()

      return completionsForType(typeRepository, field.type, decorators)
   }

   private fun completionsForType(typeRepository: TypeRepository, type: Type, decorators: List<ImportCompletionDecorator>): List<CompletionItem> {
      return when (type) {
         is EnumType -> provideEnumCompletions(typeRepository, type.qualifiedName, decorators)
         PrimitiveType.BOOLEAN -> {
            val completions = listOf(true, false).map {
               CompletionItem(it.toString()).apply {
                  kind = CompletionItemKind.Value
                  insertText = it.toString()
               }
            }
            completions
         }

         else -> emptyList() // TODO
      }
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
      val typeRepository = CompilationResultTypeRepository(compilationResult,compilationResult)
      val lookupResult = compilationResult.compiler.getNearestToken(
         params.position.line,
         params.position.character,
         params.textDocument.normalizedUriPath()
      )
      return when {
         isIncompleteFieldDefinition(lookupResult) -> completed(typeCompletionBuilder.getTypes(typeRepository,listOf(importDecorator)))
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
   private fun provideEnumCompletions(typeRepository: TypeRepository, text: String?, decorators: List<CompletionDecorator>): List<CompletionItem> {
      if (text.isNullOrEmpty()) {
         return emptyList()
      }

      val enumTypeName = typeCompletionBuilder.getTypeName(typeRepository, text)
      return if (enumTypeName == null) {
         // Haven't picked an enum yet, so lets offer the available enums
         typeCompletionBuilder.getEnumTypes(typeRepository,decorators)
      } else {
         typeCompletionBuilder.getEnumValues(typeRepository, decorators, enumTypeName)
      }
   }


   /**
    * Some cursor locations are misleading, and we need
    * to look higher to understand what the user is trying to do
    */
   fun getSignificantContext(contextAtCursor: ParserRuleContext): ParserRuleContext? {
      return when {
         contextAtCursor is LiteralContext -> {
            contextAtCursor.searchUpExcluding(
               // The value of in a key-value pair of an annotation,
               // Ignore this. If we're at a literal, it's the previous value.
               ElementValueContext::class.java
            )
         }
         else -> contextAtCursor
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

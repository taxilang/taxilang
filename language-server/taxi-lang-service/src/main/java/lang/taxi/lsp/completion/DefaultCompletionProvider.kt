package lang.taxi.lsp.completion

import lang.taxi.TaxiDocument
import lang.taxi.TaxiParser
import lang.taxi.lsp.CompilationResult
import lang.taxi.searchUpForRule
import lang.taxi.types.ImportableToken
import lang.taxi.types.PrimitiveType
import lang.taxi.types.Type
import lang.taxi.utils.log
import org.antlr.v4.runtime.ParserRuleContext
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionParams
import java.util.concurrent.CompletableFuture

/**
 * These are completions that make sense regardless of whether the
 * user is editing a query, or designing a model.
 */
class DefaultCompletionProvider(
   private val typeCompletionBuilder: TypeCompletionBuilder,
) : CompletionProvider {

   // We should progressively move the completion code out of the completion service
   // into a provider, to make this more composable
   private val editorCompletionService = EditorCompletionService(typeCompletionBuilder)
   private val functionCompletionProvider = FunctionCompletionProvider()

   override fun getCompletionsForContext(
      compilationResult: CompilationResult,
      params: CompletionParams,
      importDecorator: ImportCompletionDecorator,
      contextAtCursor: ParserRuleContext?,
      lastSuccessfulCompilation: CompilationResult?,
      typeRepository: TypeRepository
   ): CompletableFuture<List<CompletionItem>> {
      val decorators = listOf(importDecorator)
      val completions = when (contextAtCursor) {
         is TaxiParser.ElementValuePairContext -> {
            editorCompletionService.provideAnnotationFieldCompletions(contextAtCursor, decorators, compilationResult)
         }

         is TaxiParser.FieldDeclarationContext,
         is TaxiParser.FieldTypeDeclarationContext,
         is TaxiParser.TypeBodyContext,
         is TaxiParser.TypeMemberDeclarationContext,
         is TaxiParser.IdentifierContext,
         is TaxiParser.ElementValueContext,
         is TaxiParser.ListOfInheritedTypesContext,
         is TaxiParser.TypeReferenceContext,
         is TaxiParser.QualifiedNameContext -> {
            // Check if we're inside an annotation declaration.

            if (contextAtCursor.searchUpForRule<TaxiParser.AnnotationContext>() != null &&
               params.position.isBetween(
                  contextAtCursor.searchUpForRule<TaxiParser.AnnotationContext>()?.LPAREN()?.symbol,
                  contextAtCursor.searchUpForRule<TaxiParser.AnnotationContext>()?.RPAREN()?.symbol
               )
            ) {
               annotationParameterCompletion(contextAtCursor, decorators, compilationResult)
            } else {
               val typeCompletions = typeCompletionBuilder.getTypes(typeRepository, decorators)
               val functionCompletions =
                  functionCompletionProvider.buildFunctions(compilationResult.documentOrEmpty, decorators)
               typeCompletions + functionCompletions
            }
         }

         else -> emptyList()
      }
      return completed(completions)
   }


   private fun annotationParameterCompletion(
      contextAtCursor: ParserRuleContext,
      decorators: List<ImportCompletionDecorator>,
      compilationResult: CompilationResult
   ): List<CompletionItem> {
      return when (contextAtCursor) {
         is TaxiParser.ElementValueContext -> {
            // specifying the value of a an annotation field
            editorCompletionService.provideAnnotationFieldValueCompletions(
               contextAtCursor,
               decorators,
               compilationResult
            )
         }

         else -> editorCompletionService.provideAnnotationFieldCompletions(
            contextAtCursor,
            decorators,
            compilationResult
         )
      }
   }


}

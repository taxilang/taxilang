package lang.taxi.lsp.completion

import lang.taxi.lsp.CompilationResult
import org.antlr.v4.runtime.ParserRuleContext
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.util.concurrent.CompletableFuture

/**
 * External facing completion service.  Not as composable as a COmpletionProvider
 */
interface CompletionService {
   fun computeCompletions(
      compilationResult: CompilationResult,
      params: CompletionParams,
      lastSuccessfulCompilation: CompilationResult?
   ): CompletableFuture<Either<MutableList<CompletionItem>, CompletionList>>

   companion object {
      fun buildCompletionParams(
         params: CompletionParams,
         compilationResult: CompilationResult
      ): Pair<ImportCompletionDecorator, ParserRuleContext?> {
         val normalizedUriPath = params.textDocument.normalizedUriPath()
         val importDecorator = ImportCompletionDecorator(compilationResult.compiler, normalizedUriPath)

         val context = compilationResult.getNearestToken(params.textDocument, params.position)
         return Pair(importDecorator, context)
      }
   }
}

/**
 * Completion Provider - intended that there's lots of these, composable
 */
interface CompletionProvider {
   fun getCompletionsForContext(
      compilationResult: CompilationResult,
      params: CompletionParams,
      importDecorator: ImportCompletionDecorator,
      /**
       * Will be null if compilation / parsing failed to detect
       * a current parser rule
       */
      contextAtCursor: ParserRuleContext?,
      lastSuccessfulCompilation: CompilationResult?
   ): CompletableFuture<List<CompletionItem>>
}

class CompositeCompletionService(private val completionProviders: List<CompletionProvider>) : CompletionService {
   override fun computeCompletions(
      compilationResult: CompilationResult,
      params: CompletionParams,
      lastSuccessfulCompilation: CompilationResult?
   ): CompletableFuture<Either<MutableList<CompletionItem>, CompletionList>> {
      val (importDecorator, context) = CompletionService.buildCompletionParams(params, compilationResult)

      val futures =
         completionProviders.map {
            it.getCompletionsForContext(
               compilationResult,
               params,
               importDecorator,
               context,
               lastSuccessfulCompilation
            )
         }
      return CompletableFuture.allOf(*futures.toTypedArray())
         .thenApply { _ ->
            val completionItems = futures.flatMap { it.get() }
            Either.forLeft(completionItems.toMutableList())
         }
   }

   companion object {
      fun withDefaults(typeProvider: TypeProvider):CompositeCompletionService {
         return CompositeCompletionService(
            listOf(
               EditorCompletionService(typeProvider),
               DefaultCompletionProvider(typeProvider)
            )
         )
      }
   }


}

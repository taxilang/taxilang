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
      lastSuccessfulCompilation: CompilationResult?,

      /**
       * Provides the ability to override the type repository that will be used
       * for type lookups etc.
       * By default, uses a combination of the most recent compilation, and the most recent
       * successful compilation
       */
      typeRepository: TypeRepository = CompilationResultTypeRepository(compilationResult, lastSuccessfulCompilation)
   ): CompletableFuture<CompletionItemList>
}

/**
 * A list of CompletionItems, returned from a CompletionProvider
 * Allows additional signalling of exclusivity, to allow refining
 * a list of completions
 */
data class CompletionItemList(
   val completions: List<CompletionItem>,
   /**
    * Indicates if these completions are exclusive, and should
    * be the only ones displayed.
    *
    * If no exclusive items are returned from all completion providers
    * then everything is returned
    */
   val exclusive: Boolean = false
) {
   companion object {
      fun exclusive(completions: List<CompletionItem>) = CompletionItemList(completions, true)
      fun empty() = CompletionItemList(emptyList(), false)
   }

   operator fun plus(other: CompletionItemList): CompletionItemList {
      return CompletionItemList(
         this.completions + other.completions,
         this.exclusive || other.exclusive
      )
   }
}

fun List<CompletionItem>.asCompletionItemList():CompletionItemList = CompletionItemList(this)
fun List<CompletionItem>.asExclusiveCompletionItemList():CompletionItemList = CompletionItemList(this, true)

interface TypeRepositoryProvider {
   fun getTypeRepository(
      compilationResult: CompilationResult,
      lastSuccessfulCompilation: CompilationResult?
   ): TypeRepository
}

object DefaultTypeRepositoryProvider : TypeRepositoryProvider {
   override fun getTypeRepository(
      compilationResult: CompilationResult,
      lastSuccessfulCompilation: CompilationResult?
   ): TypeRepository {
      return CompilationResultTypeRepository(
         lastCompilationResult = compilationResult,
         lastSuccessfulCompilationResult = lastSuccessfulCompilation
      )
   }

}


class CompositeCompletionService(
   private val completionProviders: List<CompletionProvider>,
   private val typeRepositoryProvider: TypeRepositoryProvider = DefaultTypeRepositoryProvider
   ) : CompletionService {
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
               lastSuccessfulCompilation,
               typeRepositoryProvider.getTypeRepository(
                  compilationResult = compilationResult,
                  lastSuccessfulCompilation = lastSuccessfulCompilation
               )
            )
         }
      return CompletableFuture.allOf(*futures.toTypedArray())
         .thenApply { _ ->
            val completions = futures.map { it.get() }
            // If any of the completionLists were exclusive, only show those.
            val filteredCompletions = if (completions.any { it.exclusive }) {
               completions.filter { it.exclusive }
            } else completions
            val completionItems = filteredCompletions.flatMap { it.completions }
            Either.forLeft(completionItems.toMutableList())
         }
   }

   companion object {
      fun withDefaults(typeCompletionBuilder: TypeCompletionBuilder): CompositeCompletionService {
         return CompositeCompletionService(
            listOf(
               // C3CompletionProvider first - provides grammar-based syntactic completions
               C3CompletionProvider(),
               EditorCompletionService(typeCompletionBuilder),
               DefaultCompletionProvider(typeCompletionBuilder)
            )
         )
      }
   }


}

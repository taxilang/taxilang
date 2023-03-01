package lang.taxi.lsp.actions

import lang.taxi.lsp.CompilationResult
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.util.concurrent.CompletableFuture

class CodeActionService(
   private val providers: List<CodeActionProvider> = DEFAULT_ACTIONS
) {

   companion object {
      fun withDefaultsAnd(others: List<CodeActionProvider>) = CodeActionService(DEFAULT_ACTIONS + others)

      val DEFAULT_ACTIONS: List<CodeActionProvider> = listOf(
         RemoveUnusedImport(),
         IntroduceSemanticType(),
         ExtractInlineType()
      )
   }

   fun getActions(
      compilationResult: CompilationResult,
      params: CodeActionParams
   ): CompletableFuture<MutableList<Either<Command, CodeAction>>> {
      val actions = providers
         .filter { it.canProvideFor(compilationResult, params) }
         .mapNotNull { it.provide(compilationResult, params) }
      return CompletableFuture.completedFuture(actions.toMutableList())
   }

}

interface CodeActionProvider {
   fun canProvideFor(compilationResult: CompilationResult, params: CodeActionParams): Boolean
   fun provide(compilationResult: CompilationResult, params: CodeActionParams): Either<Command, CodeAction>?
}

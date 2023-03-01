package lang.taxi.lsp.actions

import lang.taxi.lsp.CompilationResult
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either

class RemoveUnusedImport : CodeActionProvider {
    companion object {
        const val ACTION_CODE = "RemoveUnusedImport"
    }

   override fun canProvideFor(compilationResult: CompilationResult, params: CodeActionParams): Boolean {
      return params.context.diagnostics.any { it != null && it.code != null && it.code.isLeft && it.code.left == ACTION_CODE }
   }

   override fun provide(compilationResult: CompilationResult, params: CodeActionParams): Either<Command, CodeAction>? {
      val diagnostic = params.context.diagnostics.firstOrNull {
         it.code?.left == ACTION_CODE
      } ?: return null
      return Either.forRight(CodeAction("Remove unused import").apply {
         diagnostics = listOf(diagnostic)
         isPreferred = true
         kind = CodeActionKind.SourceOrganizeImports
         edit = WorkspaceEdit(
            mapOf(
               params.textDocument.uri to listOf(
                  // Delete the line
                  TextEdit(diagnostic.range, "")
               )
            )
         )
        })
    }
}

package lang.taxi.lsp.actions

import lang.taxi.TaxiParser
import lang.taxi.TaxiParser.TypeTypeContext
import lang.taxi.lsp.CompilationResult
import lang.taxi.lsp.utils.Ranges
import lang.taxi.lsp.utils.asRange
import lang.taxi.lsp.utils.isFieldDeclaration
import lang.taxi.searchUpForRule
import lang.taxi.source
import lang.taxi.text
import org.antlr.v4.runtime.ParserRuleContext
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either

class ExtractInlineType : CodeActionProvider {
   companion object {
      const val TITLE = "Extract inline type"
   }

   override fun canProvideFor(compilationResult: CompilationResult, params: CodeActionParams): Boolean {
      val compiler = compilationResult.compiler
      val context = compiler
         .contextAt(params.range.start, params.textDocument)
      return isFieldDeclaration(context) && hasInlineTypeDefinition(context!!)
   }

   private fun hasInlineTypeDefinition(context: ParserRuleContext): Boolean {
      return context.searchUpForRule<TypeTypeContext>()
         ?.inlineInheritedType() != null
   }

   override fun provide(compilationResult: CompilationResult, params: CodeActionParams): Either<Command, CodeAction>? {
      val compiler = compilationResult.compiler
      val context = compiler
         .contextAt(params.range.start, params.textDocument)!!

      val inlineTypeDefContext = context.searchUpForRule<TaxiParser.TypeTypeContext>()!!
      val inlineTypeName = inlineTypeDefContext.classOrInterfaceType().identifier().text()
      val typeDeclarationSource = inlineTypeDefContext.source().content

      // Find a place to put the new type def.
      // We'll stick it just before the current type def.
      val modelDefinitionContext = context.searchUpForRule<TaxiParser.TypeDeclarationContext>()!!
      val typeDefinitionEdit = TextEdit(
         Ranges.insertAtTokenLocation(modelDefinitionContext.start),
         "type $typeDeclarationSource\n\n"
      )

      val replaceExistingTypeDefEdit = TextEdit(
         inlineTypeDefContext.asRange(),
         inlineTypeName
      )
      return Either.forRight(CodeAction(TITLE).apply {
         diagnostics = emptyList()
         isPreferred = true
         kind = CodeActionKind.RefactorExtract


         edit = WorkspaceEdit(
            mapOf(
               params.textDocument.uri to listOf(
                  typeDefinitionEdit,
                  replaceExistingTypeDefEdit
               )
            )
         )
      })
   }
}

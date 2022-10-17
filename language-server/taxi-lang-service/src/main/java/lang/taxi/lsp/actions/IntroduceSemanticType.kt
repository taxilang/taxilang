package lang.taxi.lsp.actions

import lang.taxi.Compiler
import lang.taxi.TaxiParser.FieldDeclarationContext
import lang.taxi.TaxiParser.TypeDeclarationContext
import lang.taxi.lsp.CompilationResult
import lang.taxi.lsp.completion.normalizedUriPath
import lang.taxi.lsp.utils.Ranges
import lang.taxi.lsp.utils.getFieldType
import lang.taxi.lsp.utils.isFieldDeclaration
import lang.taxi.lsp.utils.isPrimitiveType
import lang.taxi.searchUpForRule
import org.antlr.v4.runtime.ParserRuleContext
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either

fun Compiler.contextAt(position: Position, identifier: TextDocumentIdentifier): ParserRuleContext? {
   return this.contextAt(position.line, position.character, identifier.normalizedUriPath())

}

class IntroduceSemanticType : CodeActionProvider {
   companion object {
      val TITLE = "Introduce semantic type"
   }

   override fun canProvideFor(compilationResult: CompilationResult, params: CodeActionParams): Boolean {
      val compiler = compilationResult.compiler
      val context = compiler
         .contextAt(params.range.start, params.textDocument)
      return isFieldDeclaration(context) && getFieldType(context!!, compiler).isPrimitiveType()
   }


   override fun provide(compilationResult: CompilationResult, params: CodeActionParams): Either<Command, CodeAction>? {
      val compiler = compilationResult.compiler
      val context = compiler
         .contextAt(params.range.start, params.textDocument)!!
      val fieldContext = context.searchUpForRule<FieldDeclarationContext>()!!
      val fieldType = getFieldType(context, compiler)

      // Find a place to put the new type def.
      // We'll stick it just before the current type def.
      val modelDefinitionContext = context.searchUpForRule<TypeDeclarationContext>()!!
      val fieldName = fieldContext.identifier().IdentifierToken().text

      val newTypeName = fieldName.replaceFirstChar { it.uppercase() }
      val typeDefinitionEdit = TextEdit(
         Ranges.insertAtTokenLocation(modelDefinitionContext.start),
         "type $newTypeName inherits ${fieldType.typeName}\n\n"
      )
      val setTypeEdit = TextEdit(
         Ranges.replaceToken(fieldContext.simpleFieldDeclaration()),
         newTypeName
      )
      return Either.forRight(CodeAction(TITLE).apply {
         diagnostics = emptyList()
         isPreferred = true
         kind = CodeActionKind.RefactorExtract

         edit = WorkspaceEdit(
            mapOf(
               params.textDocument.uri to listOf(
                  typeDefinitionEdit,
                  setTypeEdit
               )
            )
         )
      })
   }
}

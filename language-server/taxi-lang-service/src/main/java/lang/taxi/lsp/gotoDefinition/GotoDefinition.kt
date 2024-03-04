package lang.taxi.lsp.gotoDefinition

import lang.taxi.Compiler
import lang.taxi.TaxiParser
import lang.taxi.TaxiParser.IdentifierContext
import lang.taxi.TaxiParser.QualifiedNameContext
import lang.taxi.TaxiParser.TypeReferenceContext
import lang.taxi.lsp.CompilationResult
import lang.taxi.lsp.completion.TypeCompletionBuilder
import lang.taxi.lsp.completion.normalizedUriPath
import lang.taxi.types.CompilationUnit
import org.antlr.v4.runtime.ParserRuleContext
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.util.concurrent.CompletableFuture

class GotoDefinitionService(private val typeCompletionBuilder: TypeCompletionBuilder) {

   fun definition(
      compilationResult: CompilationResult,
      params: DefinitionParams
   ): CompletableFuture<Either<MutableList<out Location>, MutableList<out LocationLink>>> {
      val compiler = compilationResult.compiler
      val context =
         getRuleContext(compiler, params)
      val compilationUnit = when (context) {
         is TaxiParser.FieldTypeDeclarationContext -> compiler.getDeclarationSource(context.typeExpression().nullableTypeReference().typeReference())
         is QualifiedNameContext -> {
            if (context.parent is TypeReferenceContext) {
               compiler.getDeclarationSource(context.parent as TypeReferenceContext)
            } else {
               null
            }
         }
         is TaxiParser.TypeReferenceContext -> compiler.getDeclarationSource(context)
         is TaxiParser.ListOfInheritedTypesContext -> {
            // TODO  : For now, let's just use the first type. Not sure we support a list of types here.
            val inheritedType = context.typeReference().first()
            compiler.getDeclarationSource(inheritedType)
         }

         is TaxiParser.EnumInheritedTypeContext -> {
            val inheritedType = context.typeReference()
            compiler.getDeclarationSource(inheritedType)
         }
         is TaxiParser.EnumSynonymSingleDeclarationContext -> {
            // Drop off the value within the enum for now, we'll navigate to the enum class, but not
            // the value within it.
            val enumName = context.text.split(".").dropLast(1).joinToString(".")
            compiler.getDeclarationSource(enumName, context)
         }

         else -> null
      }
      val location = if (compilationUnit != null) {
         listOf(compilationUnit.toLocation())
      } else {
         emptyList()
      }.toMutableList()
      val either = Either.forLeft<MutableList<out Location>, MutableList<out LocationLink>>(location)
      return CompletableFuture.completedFuture(either)
   }

   private fun getRuleContext(
      compiler: Compiler,
      params: DefinitionParams
   ): ParserRuleContext? {
      val context =
         compiler.contextAt(params.position.line, params.position.character, params.textDocument.normalizedUriPath())
      return if (context is IdentifierContext) {
         // Go one-up to see what type of identitifer we're building
         context.parent as ParserRuleContext?
      } else {
         context
      }

   }

}


fun CompilationUnit.toLocation(): Location {
   val position = Position(this.location.line - 1, this.location.char)
   return Location(this.source.normalizedSourceName, Range(position, position))
}

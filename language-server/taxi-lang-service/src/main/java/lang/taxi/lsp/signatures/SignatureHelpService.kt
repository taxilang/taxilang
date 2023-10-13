package lang.taxi.lsp.signatures

import lang.taxi.Compiler
import lang.taxi.TaxiParser.ArgumentContext
import lang.taxi.TaxiParser.ArgumentListContext
import lang.taxi.TaxiParser.FieldTypeDeclarationContext
import lang.taxi.TaxiParser.FunctionCallContext
import lang.taxi.TaxiParser.IdentifierContext
import lang.taxi.TaxiParser.ParameterConstraintContext
import lang.taxi.functions.Function
import lang.taxi.lsp.CompilationResult
import lang.taxi.lsp.completion.toMarkup
import lang.taxi.lsp.completion.toMarkupOrEmpty
import lang.taxi.searchUpForRule
import org.antlr.v4.runtime.ParserRuleContext
import org.eclipse.lsp4j.ParameterInformation
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.SignatureHelpParams
import org.eclipse.lsp4j.SignatureInformation
import java.util.concurrent.CompletableFuture

class SignatureHelpService {
   fun getSignatureHelp(
      lastCompilationResult: CompilationResult,
      lastSuccessfulCompilation: CompilationResult?,
      params: SignatureHelpParams
   ): CompletableFuture<SignatureHelp> {
      val empty = CompletableFuture.completedFuture(SignatureHelp())
      val schema = lastCompilationResult.document ?: return empty
      val token = lastCompilationResult.getNearestToken(params.textDocument, params.position)
         ?: return empty

      val compiler = lastCompilationResult.compiler
      val symbolText = findSymbolText(token, compiler)
         ?: return empty
      val function = if (schema.containsImportable(symbolText)) {
         schema.importableToken(symbolText)
      } else null
      if (function !is Function) return empty

      val parameters = function.parameters.map { param ->
         val paramName = if (param.name != null) {
            "${param.name}: ${param.type.toQualifiedName().typeName}"
         } else {
            param.type.toQualifiedName().typeName
         }

         ParameterInformation(
            paramName,
            param.typeDoc.toMarkupOrEmpty()
         )
      }

      val label = function.toQualifiedName().typeName + "(${parameters.joinToString(", ") { it.label.left } })"


      val signatureInformation = SignatureInformation(
         label,
         function.typeDoc.toMarkupOrEmpty(),
         parameters
      )

      val activeParameter = calculateActiveParamIndex(token)
      return CompletableFuture.completedFuture(SignatureHelp(listOf(signatureInformation), 0, activeParameter))
   }

   private fun calculateActiveParamIndex(token: ParserRuleContext): Int {
      val thisArg = token.searchUpForRule<ArgumentContext>() ?: return  -1
      val list = token.searchUpForRule<ArgumentListContext>() ?: return  -1

      return list.argument().indexOf(thisArg)
   }

   private fun findSymbolText(token: ParserRuleContext, compiler: Compiler): String? {
      // We need to find the start of the function.
      // Because of the parse tree, it could be a few different things.
      // Search up until we find one of the possible valid starts.
      val foundRule = token.searchUpForRule(listOf(
         FunctionCallContext::class.java
         ))

      val tokenWithFunctionName = when (foundRule) {
         is FieldTypeDeclarationContext -> {
            // This is a function where no params are present yet.
            // eg: firstName : left()
            // The grammar doesn't know if it's a param constraint eg: `FirstName( a == b )`
            // or a function
            foundRule.nullableTypeReference()
         }
         is IdentifierContext -> foundRule
         is FunctionCallContext -> foundRule.qualifiedName()
         null -> {
            // Depending on the tree, a function call can be interpreted as a constraint on a type.
            // eg:  left(a) looks a lot like Person(FirstName == 2)
            // That's why we end up here.
            if (token.searchUpForRule<FieldTypeDeclarationContext>() != null) {
               token.searchUpForRule<FieldTypeDeclarationContext>()!!.nullableTypeReference()
            } else {
               error("Unhandled node type when searching for function: ${token::class.simpleName}")
            }
         }
         else -> error("Unhandled node type when searching for function: ${foundRule::class.simpleName}")
      }
      val lookupResult = compiler.lookupSymbolByName(tokenWithFunctionName.text, tokenWithFunctionName)
         .getOrNull()
      return lookupResult
   }
}

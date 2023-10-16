package lang.taxi.lsp.signatures

import lang.taxi.Compiler
import lang.taxi.TaxiParser.AnnotationContext
import lang.taxi.TaxiParser.ArgumentContext
import lang.taxi.TaxiParser.ArgumentListContext
import lang.taxi.TaxiParser.ElementValuePairContext
import lang.taxi.TaxiParser.FieldTypeDeclarationContext
import lang.taxi.TaxiParser.FunctionCallContext
import lang.taxi.TaxiParser.IdentifierContext
import lang.taxi.expressions.Expression
import lang.taxi.functions.Function
import lang.taxi.lsp.CompilationResult
import lang.taxi.lsp.completion.toMarkupOrEmpty
import lang.taxi.lsp.utils.asRange
import lang.taxi.lsp.utils.contains
import lang.taxi.lsp.utils.isBefore
import lang.taxi.searchUpForRule
import lang.taxi.types.AnnotationType
import lang.taxi.types.QualifiedName
import org.antlr.v4.runtime.ParserRuleContext
import org.eclipse.lsp4j.ParameterInformation
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.SignatureHelpParams
import org.eclipse.lsp4j.SignatureInformation
import org.eclipse.lsp4j.util.Positions
import java.util.concurrent.CompletableFuture
import kotlin.math.max
import kotlin.math.min

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
      val importableToken = if (schema.containsImportable(symbolText)) {
         schema.importableToken(symbolText)
      } else null

      val (signatureInformation, activeParameter) = when (importableToken) {
         is Function -> getFunctionSignatureInformation(importableToken, token)
         is AnnotationType -> getAnnotationSignatureInformation(importableToken, token, params.position)
         else -> return empty
      }

      return CompletableFuture.completedFuture(SignatureHelp(listOf(signatureInformation), 0, activeParameter))
   }

   private fun getAnnotationSignatureInformation(
      annotationType: AnnotationType,
      token: ParserRuleContext,
      position: Position
   ): Pair<SignatureInformation, Int> {
      val params = annotationType.fields.map { field ->
         val nullable = if (field.nullable) "?" else ""
         val defaultValue = when (field.accessor) {
            is Expression -> (field.accessor as Expression).asTaxi()
            else -> null
         }?.let { " = $it" } ?: ""
         val label = "${field.name}: ${field.type.toQualifiedName().typeName}$nullable$defaultValue"

         ParameterInformation(
            label,
            field.typeDoc.toMarkupOrEmpty()
         )
      }
      val label = signatureLabel(annotationType.toQualifiedName(), params)
      val activeParam = calculateAnnotationActiveParamIndex(token, position)
      return SignatureInformation(
         label,
         annotationType.typeDoc.toMarkupOrEmpty(),
         params
      ) to activeParam
   }

   private fun signatureLabel(name: QualifiedName, params: List<ParameterInformation>): String {
      return name.typeName + "(${params.joinToString(", ") { it.label.left }})"
   }

   private fun getFunctionSignatureInformation(
      function: Function,
      token: ParserRuleContext
   ): Pair<SignatureInformation, Int> {
      val parameters = function.parameters.map { param ->
         val label = if (param.name != null) {
            "${param.name}: ${param.type.toQualifiedName().typeName}"
         } else {
            param.type.toQualifiedName().typeName
         }

         ParameterInformation(
            label,
            param.typeDoc.toMarkupOrEmpty()
         )
      }

      val label = signatureLabel(function.toQualifiedName(), parameters)

      val activeParam = calculateFunctionActiveParamIndex(token)
      return SignatureInformation(
         label,
         function.typeDoc.toMarkupOrEmpty(),
         parameters
      ) to activeParam
   }


   private fun calculateFunctionActiveParamIndex(token: ParserRuleContext): Int {
      val thisArg = token.searchUpForRule<ArgumentContext>() ?: return -1
      val list = token.searchUpForRule<ArgumentListContext>() ?: return -1

      return list.argument().indexOf(thisArg)
   }

   private fun findSymbolText(token: ParserRuleContext, compiler: Compiler): String? {
      // We need to find the start of the function.
      // Because of the parse tree, it could be a few different things.
      // Search up until we find one of the possible valid starts.
      val foundRule = token.searchUpForRule(
         listOf(
            FunctionCallContext::class.java,
            AnnotationContext::class.java
         )
      )

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
         is AnnotationContext -> foundRule.qualifiedName()
         null -> {
            // Depending on the tree, a function call can be interpreted as a constraint on a type.
            // eg:  left(a) looks a lot like Person(FirstName == 2)
            // That's why we end up here.
            if (token.searchUpForRule<FieldTypeDeclarationContext>() != null) {
               token.searchUpForRule<FieldTypeDeclarationContext>()!!.nullableTypeReference()
            } else {
               return null
            }
         }

         else -> null
      } ?: return null

      val lookupResult = compiler.lookupSymbolByName(tokenWithFunctionName.text, tokenWithFunctionName)
         .getOrNull()
      return lookupResult
   }

   companion object {
      fun calculateAnnotationActiveParamIndex(token: ParserRuleContext, position: Position): Int {
         val annotation = token.searchUpForRule<AnnotationContext>() ?: return -1;
         val thisParam = token.searchUpForRule<ElementValuePairContext>() ?: return  -1;
         val index = annotation.elementValuePairs().elementValuePair().indexOf(thisParam)

         val paramRange = thisParam.asRange()

         // If the cursor is just before or after the current param, then use the next one.
         // This happens if the user hasn't started typing the next param yet
         return when {
            paramRange.contains(position) -> index
            position.isBefore(paramRange.start) -> max(0, index -1)
            else -> min(annotation.elementValuePairs().elementValuePair().size, index + 1)
         }
      }
   }
}

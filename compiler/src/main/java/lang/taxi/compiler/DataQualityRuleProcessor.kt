package lang.taxi.compiler

import arrow.core.Either
import lang.taxi.CompilationError
import lang.taxi.Namespace
import lang.taxi.TaxiParser
import lang.taxi.dataQuality.DataQualityRule
import lang.taxi.dataQuality.DataQualityRuleScope
import lang.taxi.functions.Function
import lang.taxi.toCompilationUnits
import lang.taxi.utils.flattenErrors
import lang.taxi.utils.invertEitherList

class DataQualityRuleProcessor(private val tokenProcessor: TokenProcessor) {
   fun compileRule(
      qualifiedName: String,
      namespace: Namespace,
      ruleContext: TaxiParser.DataQualityRuleContext
   ): Either<List<CompilationError>, DataQualityRule> {
      val scope = ruleContext.ruleScope()?.text?.let { DataQualityRuleScope.forToken(it) }
      val typeDoc = tokenProcessor.parseTypeDoc(ruleContext.typeDoc())
      val annotations = tokenProcessor.collateAnnotations(ruleContext.annotation())

      val applyToFunctions = ruleContext.dataQualityRuleBody()?.dataQualityRuleApplyTo()?.let {
         buildApplyToList(it)
      } ?: Either.right(emptyList())

      return applyToFunctions.map { functions ->
         DataQualityRule(
            qualifiedName,
            annotations,
            typeDoc,
            scope,
            functions,
            ruleContext.toCompilationUnits()
         )
      }
   }

   private fun buildApplyToList(context: TaxiParser.DataQualityRuleApplyToContext): Either<List<CompilationError>, List<Function>> {
      return context.dataQualityApplyToFunctionList()
         .functionExpression()
         .map {
            tokenProcessor.resolveFunction(it.functionName().text, it)
         }
         .invertEitherList()
         .flattenErrors()

   }
}

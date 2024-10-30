package lang.taxi.linter.rules

import lang.taxi.CompilationMessage
import lang.taxi.linter.OperationLinterRule
import lang.taxi.messages.Severity
import lang.taxi.services.Operation
import lang.taxi.services.OperationScope

object OperationWithInputShouldDeclareContract : OperationLinterRule {
   override val id: String = "operation-with-input-should-declare-contract"

   override fun evaluate(source: Operation): List<CompilationMessage> {
      return if (source.scope == OperationScope.READ_ONLY && source.contract == null && source.parameters.isNotEmpty()) {
         listOf(
            CompilationMessage(
               source.compilationUnits.first(),
               "Operations with inputs should declare a response contract. Consider adding a contract defining how the return value relates to the inputs.",
               severity = Severity.WARNING
            )
         )
      } else {
         emptyList()
      }
   }
}

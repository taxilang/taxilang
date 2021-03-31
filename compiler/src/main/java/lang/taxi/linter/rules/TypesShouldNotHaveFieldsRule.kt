package lang.taxi.linter.rules

import lang.taxi.CompilationMessage
import lang.taxi.linter.ModelLinterRule
import lang.taxi.messages.Severity
import lang.taxi.types.ObjectType
import lang.taxi.types.TypeKind

object TypesShouldNotHaveFieldsRule : ModelLinterRule {
   override val id: String = "types-must-not-have-fields"

   override fun evaluate(source: ObjectType): List<CompilationMessage> {
      return if (source.definition!!.typeKind == TypeKind.Type && source.fields.isNotEmpty()) {
         return listOf(
            CompilationMessage(
               source.compilationUnits.first(),
               "Types should not have fields.  Use a model instead",
               severity = Severity.WARNING
            )
         )
      } else {
         emptyList()
      }
   }
}

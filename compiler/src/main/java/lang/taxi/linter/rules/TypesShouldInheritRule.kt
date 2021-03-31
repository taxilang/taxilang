package lang.taxi.linter.rules

import lang.taxi.CompilationMessage
import lang.taxi.linter.ModelLinterRule
import lang.taxi.messages.Severity
import lang.taxi.types.ObjectType
import lang.taxi.types.TypeKind

object TypesShouldInheritRule : ModelLinterRule {
   override val id: String = "types-should-inherit-from-something"

   override fun evaluate(source: ObjectType): List<CompilationMessage> {
      return if (source.definition!!.typeKind == TypeKind.Type && source.inheritsFrom.isEmpty()) {
         listOf(
            CompilationMessage(
               source.compilationUnits.first(),
               "Types should inherit from something - either a primitive type (String, Int, Boolean, etc) or another type",
               severity = Severity.WARNING
            )
         )
      } else {
         emptyList()
      }
   }
}

package lang.taxi.linter.rules

import lang.taxi.CompilationMessage
import lang.taxi.linter.ModelLinterRule
import lang.taxi.messages.Severity
import lang.taxi.types.ObjectType

object NoDuplicateTypesOnModelsRule : ModelLinterRule {
   override val id: String = "no-duplicate-types-on-models"

   override fun evaluate(source: ObjectType): List<CompilationMessage> {
      val fieldsByType = source.fields.groupBy { it.type }
      return fieldsByType.filterValues { it.size > 1 }
         .flatMap { (_,fields) ->
            fields.map { field -> CompilationMessage(
               field.compilationUnit,
               "${field.type.toQualifiedName().typeName} is used multiple times.  This can lead to ambiguity when querying fields semantically.  Consider trying to use more specific types",
               severity = Severity.WARNING
            ) }
         }
   }
}

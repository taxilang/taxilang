package lang.taxi.linter.rules

import lang.taxi.CompilationMessage
import lang.taxi.linter.ModelLinterRule
import lang.taxi.messages.Severity
import lang.taxi.types.ObjectType
import lang.taxi.types.PrimitiveType

object NoPrimitiveTypesOnModelsRule : ModelLinterRule {
   override val id: String = "no-primitive-types-on-models"

   override fun evaluate(source: ObjectType): List<CompilationMessage> {
      return source.fields
         .filter { it.type is PrimitiveType }
         .map {
            val typeName = it.type.toQualifiedName().typeName
            CompilationMessage(
               it.compilationUnit.location,
               "${it.name} should not inherit from a primitive type ($typeName).  Introduce a semantic type instead, which extends from $typeName.",
               severity = Severity.WARNING
            ) }
   }



}

package lang.taxi.linter.rules

import lang.taxi.CompilationMessage
import lang.taxi.linter.TypeAliasLinterRule
import lang.taxi.messages.Severity
import lang.taxi.types.PrimitiveType
import lang.taxi.types.TypeAlias

object NoTypeAliasOnPrimitivesTypeRule : TypeAliasLinterRule {
   override val id: String = "no-type-alias-on-primitives"

   override fun evaluate(source: TypeAlias): List<CompilationMessage> {
      return if (source.aliasType != null && source.aliasType is PrimitiveType) {
         listOf(
              CompilationMessage(
                 source.compilationUnits.first(),
                 "You should not declare a type alias against a primitive type.  This makes all ${source.toQualifiedName().typeName} entirely interchangeable with a ${source.aliasType!!.toQualifiedName().typeName}, which is almost never correct.  Use inherits instead.",
                 // Intentionally setting the severity to ERROR here, as this is a big deal.
                 // Users can override this using settings if they're crazy.
                 severity = Severity.ERROR
              )
         )
      } else {
         emptyList()
      }
   }
}

package lang.taxi.dataQuality

import lang.taxi.functions.Function
import lang.taxi.types.*
import lang.taxi.types.Annotation

data class DataQualityRule(
   override val qualifiedName: String,
   override val annotations: List<Annotation>,
   override val typeDoc: String?,
   val scope: DataQualityRuleScope?,
   val applyToFunctions: List<Function>,
   override val compilationUnits: List<CompilationUnit>
) : Annotatable, Named, Compiled, Documented

enum class DataQualityRuleScope(val token: String) {
   TYPE("type"),
   CONCEPT("concept"),
   MODEL("model");

   companion object {
      private val byToken = values().associateBy { it.token }
      fun forToken(token: String): DataQualityRuleScope =
         byToken[token] ?: error("No DataQualityRuleScope exists for token $token")
   }
}

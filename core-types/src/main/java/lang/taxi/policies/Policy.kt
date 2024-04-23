package lang.taxi.policies

import lang.taxi.accessors.ProjectionFunctionScope
import lang.taxi.expressions.Expression
import lang.taxi.services.OperationScope
import lang.taxi.types.*
import lang.taxi.types.Annotation

data class Policy(
   override val qualifiedName: String,
   val targetType: Type,
   val inputs: List<ProjectionFunctionScope>,
   val rules: List<PolicyRule>,
   override val annotations: List<Annotation>,
   override val compilationUnits: List<CompilationUnit>
) : Annotatable, Named, Compiled

data class PolicyRule(
   val operationScope: OperationScope?,
   val policyScope: PolicyOperationScope?,
   val expression: Expression
)


/**
 * Differentiates between data returned to a caller (External),
 * and data collected by Vyne as part of a query plan (Internal).
 * The idea being you can write more relaxed rules for Vyne to collect data,
 * and then strict rules about what is actually returned back out to the caller.
 */

enum class PolicyOperationScope(val symbol: String) {
   INTERNAL_AND_EXTERNAL("internal"),

   EXTERNAL("external");

   companion object {
      private val bySymbol = PolicyOperationScope.values().associateBy { it.symbol }
      fun parse(input: String?): PolicyOperationScope? {
         return if (input == null) null else
            bySymbol[input] ?: error("Unknown scope - $input")
      }

   }
}


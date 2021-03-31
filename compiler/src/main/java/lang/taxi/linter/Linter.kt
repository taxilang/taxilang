package lang.taxi.linter

import lang.taxi.CompilationMessage
import lang.taxi.linter.rules.NoDuplicateTypesOnModelsRule
import lang.taxi.linter.rules.NoPrimitiveTypesOnModelsRule
import lang.taxi.linter.rules.NoTypeAliasOnPrimitivesTypeRule
import lang.taxi.linter.rules.TypesShouldInheritRule
import lang.taxi.linter.rules.TypesShouldNotHaveFieldsRule
import lang.taxi.types.EnumType
import lang.taxi.types.ObjectType
import lang.taxi.types.Type
import lang.taxi.types.TypeAlias

class Linter(
   val config: List<LinterRuleConfiguration> = emptyList()
) {
   val configByRule = config.associateBy { it.id }
   val rules: Map<LinterRule<out Any>, LinterRuleConfiguration> = LinterRules.ALL_RULES
      .map { rule ->
         rule to configByRule.getOrElse(rule.id) { LinterRuleConfiguration(rule.id, enabled = true, severity = null) }
      }
      .filter { (_, config) -> config.enabled }
      .toMap()

   fun lint(type: Type): List<CompilationMessage> {
      return when (type) {
         is ObjectType -> rules.filterKeys { it is ModelLinterRule }.evaluate(type)
         is TypeAlias -> rules.filterKeys { it is TypeAliasLinterRule }.evaluate(type)
         is EnumType -> rules.filterKeys { it is EnumLinterRule }.evaluate(type)
         else -> rules.filterKeys { it is TypeLinterRule }.evaluate(type)
      }
   }

   companion object {
      fun empty() = Linter(LinterRules.allDisabled())
   }
}

private fun Map<LinterRule<out Any>, LinterRuleConfiguration>.evaluate(type: Type): List<CompilationMessage> {
   return this.flatMap { (rule, config) ->
      (rule as LinterRule<Type>).evaluate(type).map { message ->
         val messageWithRuleName = message.detailMessage + " (linter rule ${rule.id})"
         message.copy(severity = config.severity ?: message.severity, detailMessage = messageWithRuleName)
      }
   }
}

object LinterRules {
   fun allDisabled(): List<LinterRuleConfiguration> {
      return ALL_RULES.map { LinterRuleConfiguration(it.id, enabled = false) }
   }

   fun onlyEnable(vararg enabledRules: LinterRule<out Any>): List<LinterRuleConfiguration> {
      return ALL_RULES.map { linterRule ->
         val enabled = enabledRules.contains(linterRule)
         LinterRuleConfiguration(linterRule.id, enabled = enabled)
      }
   }

   val ALL_RULES = listOf(
      NoPrimitiveTypesOnModelsRule,
      NoDuplicateTypesOnModelsRule,
      NoTypeAliasOnPrimitivesTypeRule,
      TypesShouldInheritRule,
      TypesShouldNotHaveFieldsRule
   )
}


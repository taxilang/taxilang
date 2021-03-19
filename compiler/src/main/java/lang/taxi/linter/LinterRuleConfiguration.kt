package lang.taxi.linter

import lang.taxi.messages.Severity

/**
 * Allows for overriding behaviour of certain linting rules
 */
data class LinterRuleConfiguration(
   val id: String,
   /**
    * Allows config to override the severity for a specific rule
    */
   val severity: Severity? = null,
   val enabled: Boolean = true
)

/**
 * Map from the linter rule lightweight object used in taxi.conf
 * to the LinterRuleConfiguration class, which includes the id.
 */
fun Map<String, TaxiConfLinterRuleConfig>.toLinterRules(): List<LinterRuleConfiguration> {
   return this.map { (id, config) ->
      LinterRuleConfiguration(id, config.severity, config.enabled)
   }
}

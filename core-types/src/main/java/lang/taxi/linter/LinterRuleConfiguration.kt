package lang.taxi.linter

import lang.taxi.messages.Severity

/**
 * Named to disambiguate between this, and the LinterRuleConfiguraton in the compiler,
 * which this class ultimately creates
 */
data class TaxiConfLinterRuleConfig(val enabled: Boolean = true, val severity: Severity = Severity.WARNING)

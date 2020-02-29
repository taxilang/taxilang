package lang.taxi

import org.junit.Test

class ConditionalDataTypesTest {

   @Test
   fun simpleConditionalTypeGrammar() {
      val src = """
type TradeRecord {
    // define simple properties by xpath expressions
    ccy1 : Currency as String
    (   dealtAmount : String
        settlementAmount : String
    ) by when( xpath("/foo/bar") : String) {
        ccy1 -> {
            dealtAmount(
                quantity by xpath("/foo/bar/quantity1")
                currency = ccy1
            )
            settlementAmount(
                quantity by xpath("/foo/bar/quantity2")
                currency = "GBP"
            )
        }
        "foo" -> {
            dealtAmount(
                quantity by xpath("/foo/bar/quantity2")
                currency = ccy1
            )
            settlementAmount(
                quantity by xpath("/foo/bar/quantity1")
                currency = "EUR"
            )
        }
    }
}
      """.trimIndent()
      val doc = Compiler(src).compile()
      val type = doc.type("TradeRecord")
      TODO()
   }
   @Test
   fun canDeclareConditionalTypes() {
      val src = """
type Money {
    quantity : MoneyAmount as Decimal
    currency : CurrencySymbol as String
}

type DealtAmount inherits Money
type SettlementAmount inherits Money
type alias Ccy1 as String
type alias Ccy2 as String

enum QuoteBasis {
    Currency1PerCurrency2,
    Currency2PerCurrency1
}
type TradeRecord {
    // define simple properties by xpath expressions
    ccy1 : Ccy1 by xpath('/foo/bar/ccy1')
    ccy2 : Ccy2 by xpath('/foo/bar/ccy2')
    (   dealtAmount : DealtAmount
        settlementAmount : SettlementAmount
        quoteBasis: QuoteBasis
    ) by when( xpath("/foo/bar") : CurrencySymbol) {
        ccy1 -> {
            dealtAmount(
                quantity by xpath("/foo/bar/quantity1")
                currency = ccy1
            )
            settlementAmount(
                quantity by xpath("/foo/bar/quantity2")
                currency = ccy2
            )
            quoteBasis = QuoteBasis.Currency1PerCurrency2
        }
        ccy2 -> {
            dealtAmount(
                quantity by xpath("/foo/bar/quantity2")
                currency = ccy1
            )
            settlementAmount(
                quantity by xpath("/foo/bar/quantity1")
                currency = ccy2
            )
            quoteBasis  = QuoteBasis.Currency2PerCurrency1
        }
    }
}
      """.trimIndent()
      val doc = Compiler(src).compile()
   }
}

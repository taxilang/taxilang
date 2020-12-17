package lang.taxi.xsd

import lang.taxi.Compiler
import org.junit.Test

class TypeDocsTest {
   @Test
   fun `will sanitize typedocs`() {
      val content = """The simple return formula is: [ [P sub t - P sub (t-1)] / [P sub (t-1)]] - 1 """
      val sanitized = TypeDocs.sanitize(content)

      val taxi = Compiler("""[[ $sanitized ]] type Foo"""  ).compile()
      val typedoc = taxi.type("Foo")
         .typeDoc
      TODO()
   }
   @Test
   fun `foo`() {
      val src = """   [[ Defines the value of the commodity return calculation formula as simple or compound. The simple return formula is: [ [P sub t - P sub (t-1)] / [P sub (t-1)] ] - 1 where: P sub t is the price or index level at time period t and P sub t-1 is the price or index level in time period t-1. The compound return formula is the geometric average return for the period: PI from d=1 to d=n [ [ [ [P sub t - P sub (t-1)] / [P sub (t-1)] ] + 1] sup (1 / n) ] - 1 where: PI is the product operator, p sub t is the price or index level at time period t, p sub t -1 is the price or index level at time period t-1 ]]
   enum CommodityReturnCalculationFormulaEnum {
      [[ The value is when the cash settlement amount is the simple formula: Notional Amount * ((Index Level sub d / Index Level sub d-1) - 1). That is, when the cash settlement amount is the Notional Amount for the calculation period multiplied by the ratio of the index level on the reset date/valuation date divided by the index level on the immediately preceding reset date/valuation date minus one. ]]
      SimpleFormula,
      [[ The value is when the cash settlement amount is the compound formula: ]]
      CompoundFormula
   }

   [[ The compounding calculation method ]]
   enum CompoundingMethodEnum {
      [[ Flat compounding. Compounding excludes the spread. Note that the first compounding period has it's interest calculated including any spread then subsequent periods compound this at a rate excluding the spread. ]]
      Flat,
      [[ No compounding is to be applied. ]]
      None,
      [[ Straight compounding. Compounding includes the spread. ]]
      Straight,
      [[ Spread Exclusive compounding. ]]
      SpreadExclusive
   }
"""
      val doc = Compiler(src).validate()
      TODO()
   }
}

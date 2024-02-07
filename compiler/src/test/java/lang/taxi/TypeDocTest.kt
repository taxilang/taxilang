package lang.taxi

import com.winterbe.expekt.expect
import com.winterbe.expekt.should
import org.junit.jupiter.api.Test

class TypeDocTest {
   @Test
   fun canDeclareCommentOnType() {
      val source = """
[[
This is a multi-line comment.
It contains any text, but is treated as markdown.
It should be allowed to [contain square brackets]
]]
type Thing {
}
        """.trimIndent()
      val taxi = Compiler(source).compile()
      val thing = taxi.objectType("Thing")
      val expected = """This is a multi-line comment.
It contains any text, but is treated as markdown.
It should be allowed to [contain square brackets]""".trimIndent()
      expect(thing.typeDoc).to.equal(expected)
   }

   @Test
   fun `can use reserved words in comments`() {
      val type = """
[[ type foo type alias function ]]
type Foo
      """.compiled()
         .type("Foo")
         .typeDoc.should.equal("type foo type alias function")
   }

   @Test
   fun `can use special characters in comments`() {
      listOf(
         "[[ ] ]",
         "[[ ]",
         "[] [] [ [ ] ]",
         """\""",
         "Something about plural's",
         "Something about semicolon's; and plural's",
         "The simple return formula is: [ [P sub t - P sub (t-1)] / [P sub (t-1)] ] - 1 where: to d=n [ [ [ [P sub t - P sub (t-1)] / [P sub (t-1)] ] + 1] sup (1 / n) ] - 1 where:"
      ).forEach { commentText ->
         val  src = """[[ $commentText ]]
            |type Foo
            |
            |[[ another comment ]]
            |type Bar
         """.trimMargin()
         val compiled = src.compiled()
            .type("Foo")
         compiled.typeDoc.should.equal(commentText)
      }
   }

   @Test
   fun `test single-line comments with forward-slashes within`() {
      // Using 2 forward slashes in a url can get treated like a comment start token, ignoring the rest
      // of the line.
      """[[ See http://google.com ]]
         |type Url inherits String
      """.trimMargin()
         .compiled()
         .type("Url")
         .typeDoc.should.equal("See http://google.com")
   }

   @Test
   fun `test grammar with quotes`() {
      val taxi = """[[
It's a number (generally in the range of 100 - 2000) that
P-U-I-D, or "pooh-ed")
]]
enum PUID {
   FxSpot(919)
}

[[
It's a number (generally in the range of 100 - 2000) that
P-U-I-D, or "pooh-ed")
]]
type Puid
""".compiled()
      taxi.types.size

   }

   @Test
   fun canDeclareCommentOnTypeAlias() {
      val source = """
[[ This is a comment ]]
type Foo inherits String
      """.trimIndent()
      val taxi = Compiler(source).compile()
      val thing = taxi.typeAlias("Foo")
      val expected = """This is a comment""".trimIndent()
      expect(thing.typeDoc).to.equal(expected)
   }

   @Test
   fun canDeclareMarkdownLinksInComment() {
      val commentText = """These are [currencies](https://www.investopedia.com/terms/i/isocurrencycode.asp) used"""
      val source = """
[[
$commentText
]]
type BaseCurrency inherits String
      """.trimIndent()
      val taxi = Compiler(source).compile()
      val thing = taxi.typeAlias("BaseCurrency")
      expect(thing.typeDoc?.trim()).to.equal(commentText.trim())
   }

   @Test
   fun canDeclareBlockTypeComment() {
      val commentText = """
In the forex market, currency unit prices are quoted as currency pairs.
The base currency – also called the transaction currency - is the first currency
appearing in a currency pair quotation, followed by the second part of the quotation,
called the quote currency or the counter currency.
"""
      val source = """
[[
$commentText
]]
type BaseCurrency inherits String
      """.trimIndent()
      val taxi = Compiler(source).compile()
      val thing = taxi.typeAlias("BaseCurrency")
      expect(thing.typeDoc?.trim()).to.equal(commentText.trim())
   }

   @Test // bugfix
   fun canDeclareTypeAfterTypeAliasWithComments() {
      val source = """
[[ Comment ]]
type alias CurrencySymbol as String

[[ Another Comment ]]
type Foo
      """.trimIndent()
      val taxi = Compiler(source).compile()
      taxi.containsType("CurrencySymbol").should.be.`true`
      taxi.containsType("Foo").should.be.`true`
   }

   @Test
   fun canHaveQuotesInTypeDoc() {
      val source = """
[[ "this is a comment" ]]
type CurrencySymbol inherits String

[[ Another Comment ]]
type Foo
      """.trimIndent()
      val taxi = Compiler(source).compile()
      taxi.containsType("CurrencySymbol").should.be.`true`
      taxi.containsType("Foo").should.be.`true`
   }

   @Test
   fun trimsIndent() {
      val source = """
[[
    ISO currency codes are the three-letter alphabetic codes that
    are used throughout the world
]]
enum CurrencyCode {
   USD,
   GBP
}
      """.trimIndent()
      val taxi = Compiler(source).compile()
      taxi.enumType("CurrencyCode").typeDoc.should.equal("ISO currency codes are the three-letter alphabetic codes that\n" +
         "are used throughout the world")

   }

   @Test
   fun canDeclareCommentOnEnum() {
      val source = """
[[ This is the enum comment ]]
enum Color {

   [[ Reddish ]]
   Red,

   [[ Bluish ]]
   Blue,

   Green
}
      """.trimIndent()
      val taxi = Compiler(source).compile()
      val enumType = taxi.enumType("Color")
      val expected = """This is the enum comment""".trimIndent()
      expect(enumType.typeDoc).to.equal(expected)
      expect(enumType.value("Red").typeDoc).to.equal("Reddish")
      expect(enumType.value("Blue").typeDoc).to.equal("Bluish")
      expect(enumType.value("Green").typeDoc.isNullOrEmpty()).to.be.`true`
   }

   @Test
   fun canDeclareTypeDocOnAttribute() {
      val source = """
         type Person {
            [[ The persons given name ]]
            firstName : FirstName inherits String
         }
      """.trimIndent()
      val taxi = Compiler(source).compile()
      val firstName = taxi.objectType("Person").field("firstName")
      firstName.typeDoc.should.equal("The persons given name")
   }

   @Test
   fun `taxidoc regression test investigation`() {
      """   [[ Defines the value of the commodity return calculation formula as simple or compound. The simple return formula is: [ [P sub t - P sub (t-1)] / [P sub (t-1)] ] - 1 where: P sub t is the price or index level at time period t and P sub t-1 is the price or index level in time period t-1. The compound return formula is the geometric average return for the period: PI from d=1 to d=n [ [ [ [P sub t - P sub (t-1)] / [P sub (t-1)] ] + 1] sup (1 / n) ] - 1 where: PI is the product operator, p sub t is the price or index level at time period t, p sub t -1 is the price or index level at time period t-1 ]]
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
   }""".validated().should.be.empty
   }
}

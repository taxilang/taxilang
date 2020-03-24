package lang.taxi

import com.winterbe.expekt.expect
import com.winterbe.expekt.should
import org.junit.Test

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
   fun canDeclareCommentOnTypeAlias() {
      val source = """
[[ This is a comment ]]
type alias Foo as String
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
type alias BaseCurrency as String
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
type alias BaseCurrency as String
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
type alias CurrencySymbol as String

[[ Another Comment ]]
type Foo
      """.trimIndent()
      val taxi = Compiler(source).compile()
      taxi.containsType("CurrencySymbol").should.be.`true`
      taxi.containsType("Foo").should.be.`true`
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
}

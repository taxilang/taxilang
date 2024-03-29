package lang.taxi.types

import com.winterbe.expekt.should
import lang.taxi.sources.SourceCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EnumTypeTest {

   @Test
   fun lookupByNameOrValueWithName() {
      val enumType = enumType(listOf(
         EnumValue("USD", "US Dollars", "Currency.USD", emptyList(), emptyList()),
         EnumValue("EUR", "Euro", "Currency.EUR", emptyList(), emptyList())
      ))

      enumType.has("USD").should.be.`true`
      enumType.of("USD").name.should.equal("USD")
      enumType.of("USD").value.should.equal("US Dollars")
   }

   @Test
   fun lookupByNameOrValueWithNameNoValue() {
      // Enum has no value, with shuld default to name
      val enumType = enumType(listOf(
         EnumValue(name = "USD", qualifiedName = "Currency.USD", annotations = emptyList(), synonyms = emptyList()),
         EnumValue(name = "EUR", qualifiedName = "Currency.EUR", annotations = emptyList(), synonyms = emptyList())
      ))

      enumType.has("USD").should.be.`true`
      enumType.of("USD").name.should.equal("USD")
      enumType.of("USD").value.should.equal("USD")
   }

   @Test
   fun lookupByNameOrValueWithValue() {
      val enumType = enumType(listOf(
         EnumValue("USD", "US Dollars", "Currency.USD", emptyList(), emptyList()),
         EnumValue("EUR", "Euro", "Currency.EUR", emptyList(), emptyList())
      ))

      enumType.has("US Dollars").should.be.`true`
      enumType.of("US Dollars").name.should.equal("USD")
      enumType.of("US Dollars").value.should.equal("US Dollars")
   }

   @Test
   fun lookupByNameOrValueDoesntExist() {
      val enumType = enumType(listOf(
         EnumValue("USD", "US Dollars", "Currency.USD", emptyList(), emptyList()),
         EnumValue("EUR", "Euro", "Currency.EUR", emptyList(), emptyList())
      ))

      enumType.has("GBP").should.be.`false`
      val exception = assertThrows<IllegalStateException> {
         enumType.of("GBP")
      }
      exception.message.should.equal("Enum Currency does not contain either a name nor a value of GBP")
   }

   @Test
   fun lookupByName() {
      val enumType = enumType(listOf(
         EnumValue("USD", "US Dollars", "Currency.USD", emptyList(), emptyList()),
         EnumValue("EUR", "Euro", "Currency.EUR", emptyList(), emptyList())
      ))

      enumType.hasName("USD").should.be.`true`
      enumType.ofName("USD").name.should.equal("USD")
      enumType.ofName("USD").value.should.equal("US Dollars")
   }

   @Test
   fun lookupByNameWithValue() {
      val enumType = enumType(listOf(
         EnumValue("USD", "US Dollars", "Currency.USD", emptyList(), emptyList()),
         EnumValue("EUR", "Euro", "Currency.EUR", emptyList(), emptyList())
      ))

      enumType.hasName("US Dollars").should.be.`false`
      val exception = assertThrows<IllegalStateException> {
         enumType.ofName("US Dollars")
      }
      exception.message.should.equal("Enum Currency does not contains a member named US Dollars")
   }

   @Test
   fun lookupByValue() {
      val enumType = enumType(listOf(
         EnumValue("USD", "US Dollars", "Currency.USD", emptyList(), emptyList()),
         EnumValue("EUR", "Euro", "Currency.EUR", emptyList(), emptyList())
      ))

      enumType.hasValue("Euro").should.be.`true`
      enumType.ofValue("Euro").name.should.equal("EUR")
      enumType.ofValue("Euro").value.should.equal("Euro")
   }

   @Test
   fun lookupByValueWithName() {
      val enumType = enumType(listOf(
         EnumValue("USD", "US Dollars", "Currency.USD", emptyList(), emptyList()),
         EnumValue("EUR", "Euro", "Currency.EUR", emptyList(), emptyList())
      ))

      enumType.hasValue("EUR").should.be.`false`
      val exception = assertThrows<IllegalStateException> {
         enumType.ofValue("EUR")
      }
      exception.message.should.equal("Enum Currency does not contain a member with a value of EUR")
   }

   @Test
   fun lookupWithNulls() {
      val enumType = enumType(listOf(
         EnumValue("USD", "US Dollars", "Currency.USD", emptyList(), emptyList()),
         EnumValue("EUR", "Euro", "Currency.EUR", emptyList(), emptyList())
      ))

      enumType.has(null).should.be.`false`
      enumType.hasValue(null).should.be.`false`
      enumType.hasName(null).should.be.`false`
   }

   private fun enumType(values: List<EnumValue>): EnumType {
      val definition = EnumDefinition(
         values = values,
         compilationUnit = CompilationUnit(SourceCode("", "")),
         basePrimitive = PrimitiveType.STRING
      )
      return EnumType("Currency", definition)
   }


}

package lang.taxi.generators

import com.winterbe.expekt.should
import lang.taxi.Compiler
import org.junit.Test
import kotlin.test.fail

// Note - this is also more heavily tested via the Kotlin to Taxi projects.
class SchemaWriterTest {
   @Test
   fun generatesAnnotationsOnFields() {
      val src = Compiler("""
         type Person {
            @Id
            firstName : String
         }
      """.trimIndent()).compile()
      val generated = SchemaWriter().generateSchemas(listOf(src))[0]
      val expected = """type Person {
      @Id firstName : String
   }"""
      generated.trimNewLines().should.equal(expected.trimNewLines())
   }

   @Test
   fun `types without namespace do not generate namespace declaration`() {
      val src = Compiler("""type Person""".trimIndent()).compile()
      val generated = SchemaWriter().generateSchemas(listOf(src))[0]
      val expected = """type Person"""
      generated.trimNewLines().should.equal(expected.trimNewLines())
   }

   @Test
   fun `types with namespace generate namespace declaration`() {
      val src = Compiler("""namespace foo {
type Person
}""".trimIndent()).compile()
      val generated = SchemaWriter().generateSchemas(listOf(src))[0]
      val expected = """namespace foo {
type Person
}"""
      generated.trimNewLines().should.equal(expected.trimNewLines())
   }

   @Test
   fun outputsFormatsOnTypes() {
      val src = Compiler("""
         type DateOfBirth inherits Date
         type TimeOfBirth inherits Time
         type BirthTimestamp inherits Instant
         type WeightInGrams inherits Decimal
         type WeightInOunces inherits Decimal
         type OuncesToGramsMultiplier inherits Decimal
         type Person {
            birthDate : DateOfBirth( @format = "dd-mm-yyyy" )
            timeOfBirth : TimeOfBirth( @format = "hh:mm:ss" )
            startupTime : BirthTimestamp by (this.birthDate + this.timeOfBirth)
            weightInGrams : WeightInGrams by xpath("/foo")
            weightInOunces : WeightInOunces as (WeightInGrams * OuncesToGramsMultiplier)
         }
      """.trimIndent()).compile()
      val generated = SchemaWriter().generateSchemas(listOf(src))[0]
      val expected = """type DateOfBirth inherits lang.taxi.Date
type TimeOfBirth inherits lang.taxi.Time
type BirthTimestamp inherits lang.taxi.Instant
type WeightInGrams inherits lang.taxi.Decimal
type WeightInOunces inherits lang.taxi.Decimal
type OuncesToGramsMultiplier inherits lang.taxi.Decimal

type Person {
   birthDate : DateOfBirth( @format = "dd-mm-yyyy" )
   timeOfBirth : TimeOfBirth( @format = "hh:mm:ss" )
   startupTime : BirthTimestamp  by (this.birthDate + this.timeOfBirth)
   weightInGrams : WeightInGrams  by xpath("/foo")
   weightInOunces : WeightInOunces as (WeightInGrams * OuncesToGramsMultiplier )
}
"""
      generated.trimNewLines().should.equal(expected.trimNewLines())
      generated.shouldCompile()
   }

   @Test
   fun `outputs default values`() {
      val src = Compiler("""
         type StringField inherits String
         type NumberField inherits Int
         type Thing {
            stringThing : StringField by default("foo")
            numberThing : NumberField by default(2)
         }
      """.trimIndent()).compile()
      val generated = SchemaWriter().generateSchemas(listOf(src))[0]
      val expected = """
         type StringField inherits lang.taxi.String

         type NumberField inherits lang.taxi.Int

         type Thing {
            stringThing : StringField  by default("foo")
            numberThing : NumberField  by default(2)
         }
      """
      generated.trimNewLines().should.equal(expected.trimNewLines())
      generated.shouldCompile()
   }

   @Test
   fun `outputs concatenated columns`() {
      val src = """
         type Thing {
            primaryKey: String by concat(column(0), "-", column("NAME"), "-", column(2))
         }
      """.trimIndent()
      val schema = Compiler(src).compile()
      val generated = SchemaWriter().generateSchemas(listOf(schema))[0]
      val expected = """
         type Thing {
           primaryKey : String  by concat( column(0),"-",column("NAME"),"-",column(2) )
         }
      """
      generated.trimNewLines().should.equal(expected.trimNewLines())
      generated.shouldCompile()
   }

   @Test
   fun `outputs multiple formats`() {
      val src = """type TransactionEventDateTime inherits Instant
            type Order {
                orderDateTime : TransactionEventDateTime( @format = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSS", @format = "yyyy-MM-dd'T'HH:mm:ss.SSS")
            }"""
      val schema = Compiler(src).compile()
      val generated = SchemaWriter().generateSchemas(listOf(schema))[0]
      val expected = """
         type TransactionEventDateTime inherits lang.taxi.Instant

         type Order {
            orderDateTime : TransactionEventDateTime( @format = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSS", @format = "yyyy-MM-dd'T'HH:mm:ss.SSS" )
         }
      """.trimIndent()
      generated.trimNewLines().should.equal(expected.trimNewLines())
      generated.shouldCompile()
   }

   @Test
   fun `outputs coalesce statements`() {
      val generated = """
               type Qty inherits Decimal
               type QtyHit inherits Decimal
               type QtyFill inherits Decimal
               type SomeQty inherits Decimal
               type SomeAnotherQty inherits SomeQty

               model Foo {
                  field1: SomeAnotherQty as coalesce(Qty, QtyHit, QtyFill)
               }

            """.trimIndent()
         .compileAndRegenerateSource()

      val expected = """
        type Qty inherits lang.taxi.Decimal

type QtyHit inherits lang.taxi.Decimal

type QtyFill inherits lang.taxi.Decimal

type SomeQty inherits lang.taxi.Decimal

type SomeAnotherQty inherits SomeQty

type Foo {
   field1 : SomeAnotherQty as coalesce(Qty, QtyHit, QtyFill)
}

      """.trimIndent()
      generated.trimNewLines().should.equal(expected.trimNewLines())
      generated.shouldCompile()
   }

   @Test
   fun `outputs concat3 statements`() {
      val generated = """
               type TradeId inherits String
               type OrderId inherits String
               type MarketId inherits String

               model Record {
                  tradeId: TradeId
                  orderId: OrderId
                  marketId: MarketId
                  id: String by concat3(this.tradeId, this.orderId, this.marketId, "-")
               }

            """.compileAndRegenerateSource()
      val expected = """
         type TradeId inherits lang.taxi.String

         type OrderId inherits lang.taxi.String

         type MarketId inherits lang.taxi.String

         type Record {
            tradeId : TradeId
            orderId : OrderId
            marketId : MarketId
            id : String  by concat3(this.tradeId, this.orderId, this.marketId, "-")
         }
      """.trimIndent()
      generated.trimNewLines().should.equal(expected.trimNewLines())
      generated.shouldCompile()
   }

   @Test
   fun `outputs left statements`() {
      val generated = """
               type FirstName inherits String
               type FullName inherits String

               model Person {
                  firstName: FirstName
                  leftName : FullName by left(this.firstName, 5)
               }

            """.compileAndRegenerateSource()
      val expected = """type FirstName inherits lang.taxi.String

type FullName inherits lang.taxi.String

type Person {
   firstName : FirstName
   leftName : FullName  by left(this.firstName, 5)
}
"""
      generated.trimNewLines().should.equal(expected.trimNewLines())
      generated.shouldCompile()
   }
}

private fun String.shouldCompile() {
   val errors = Compiler(this).validate()
   if (errors.isNotEmpty()) {
      fail("Expected source to compile, but found ${errors.size} compilation errors: \n ${errors.joinToString("\n") { it.detailMessage }}")
   }
}

private fun String.compileAndRegenerateSource(): String {
   val schema = Compiler(this).compile()
   return SchemaWriter().generateSchemas(listOf(schema))[0]
}


fun String.trimNewLines(): String {
   return this
      .lines()
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .joinToString("")
}

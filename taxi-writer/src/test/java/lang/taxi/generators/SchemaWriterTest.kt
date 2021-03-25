package lang.taxi.generators

import com.winterbe.expekt.should
import lang.taxi.Compiler
import lang.taxi.messages.Severity
import org.junit.Test
import kotlin.test.fail

// Note - this is also more heavily tested via the Kotlin to Taxi projects.
class SchemaWriterTest {
   @Test
   fun generatesAnnotationsOnFields() {
      val src = Compiler("""
         model Person {
            @Id
            firstName : String
         }
      """.trimIndent()).compile()
      val generated = SchemaWriter().generateSchemas(listOf(src))[0]
      val expected = """model Person {
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
         model Person {
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

model Person {
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
         model Thing {
            stringThing : StringField by default("foo")
            numberThing : NumberField by default(2)
         }
      """.trimIndent()).compile()
      val generated = SchemaWriter().generateSchemas(listOf(src))[0]
      val expected = """
         type StringField inherits lang.taxi.String

         type NumberField inherits lang.taxi.Int

         model Thing {
            stringThing : StringField  by default("foo")
            numberThing : NumberField  by default(2)
         }
      """
      generated.trimNewLines().should.equal(expected.trimNewLines())
      generated.shouldCompile()
   }


   @Test
   fun `outputs multiple formats`() {
      val src = """type TransactionEventDateTime inherits Instant
            model Order {
                orderDateTime : TransactionEventDateTime( @format = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSS", @format = "yyyy-MM-dd'T'HH:mm:ss.SSS")
            }"""
      val schema = Compiler(src).compile()
      val generated = SchemaWriter().generateSchemas(listOf(schema))[0]
      val expected = """
         type TransactionEventDateTime inherits lang.taxi.Instant

         model Order {
            orderDateTime : TransactionEventDateTime( @format = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSS", @format = "yyyy-MM-dd'T'HH:mm:ss.SSS" )
         }
      """.trimIndent()
      generated.trimNewLines().should.equal(expected.trimNewLines())
      generated.shouldCompile()
   }

   @Test
   fun `outputs multiple formats with offset`() {
      val src = """type TransactionEventDateTime inherits Instant
            model Order {
                orderDateTime : TransactionEventDateTime( @format = ["yyyy-MM-dd'T'HH:mm:ss.SSSSSSS", "yyyy-MM-dd'T'HH:mm:ss.SSS"] @offset = 60)
            }"""
      val schema = Compiler(src).compile()
      val generated = SchemaWriter().generateSchemas(listOf(schema))[0]
      val expected = """type TransactionEventDateTime inherits lang.taxi.Instant
      model Order {
         orderDateTime : TransactionEventDateTime( @format = ["yyyy-MM-dd'T'HH:mm:ss.SSSSSSS", "yyyy-MM-dd'T'HH:mm:ss.SSS"] @offset = 60 )
      }""".trimIndent()
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

model Foo {
   field1 : SomeAnotherQty as coalesce(Qty, QtyHit, QtyFill)
}

      """.trimIndent()
      generated.trimNewLines().should.equal(expected.trimNewLines())
      generated.shouldCompile()
   }

   @Test
   fun `outputs complex when statements statements`() {
      val generated = """
               model ComplexWhen {
            trader: String
            status: String
            initialQuantity: Decimal
            leavesQuantity: Decimal
            quantityStatus: String by when {
                this.initialQuantity = this.leavesQuantity -> "ZeroFill"
                this.trader = "Marty" || this.status = "Pending" -> "ZeroFill"
                this.leavesQuantity > 0 && this.leavesQuantity < this.initialQuantity -> "PartialFill"
                this.leavesQuantity > 0 && this.leavesQuantity < this.initialQuantity || this.trader = "Marty" || this.status = "Pending"  -> "FullyFilled"
                this.leavesQuantity = null && this.initialQuantity != null -> trader
                else -> "CompleteFill"
            }
         }
            """.trimIndent()
         .compileAndRegenerateSource()

      val expected = """
        model ComplexWhen {
            trader : String
            status : String
            initialQuantity : Decimal
            leavesQuantity : Decimal
            quantityStatus : String  by when {
                this.initialQuantity = this.leavesQuantity -> "ZeroFill"
                this.trader = "Marty" || this.status = "Pending" -> "ZeroFill"
                this.leavesQuantity > 0 && this.leavesQuantity < this.initialQuantity -> "PartialFill"
                this.leavesQuantity > 0 && this.leavesQuantity < this.initialQuantity || this.trader = "Marty" || this.status = "Pending" -> "FullyFilled"
                this.leavesQuantity = null && this.initialQuantity != null -> trader
                else -> "CompleteFill"
            }
         }
      """.trimIndent()
      generated.trimNewLines().should.equal(expected.trimNewLines())
      generated.shouldCompile()
   }

   @Test
   fun outputsFormatsOnNullableTypes() {
      val src = Compiler("""
         type DateOfBirth inherits Date
         type TimeOfBirth inherits Time
         type BirthTimestamp inherits Instant
         type WeightInGrams inherits Decimal
         type WeightInOunces inherits Decimal
         type OuncesToGramsMultiplier inherits Decimal
         model Person {
            birthDate : DateOfBirth?( @format = "dd-mm-yyyy" )
            timeOfBirth : TimeOfBirth?( @format = "hh:mm:ss" )
            startupTime : BirthTimestamp? by (this.birthDate + this.timeOfBirth)
            weightInGrams : WeightInGrams? by xpath("/foo")
            weightInOunces : WeightInOunces? as (WeightInGrams * OuncesToGramsMultiplier)
         }
      """.trimIndent()).compile()
      val generated = SchemaWriter().generateSchemas(listOf(src))[0]
      val expected = """type DateOfBirth inherits lang.taxi.Date
type TimeOfBirth inherits lang.taxi.Time
type BirthTimestamp inherits lang.taxi.Instant
type WeightInGrams inherits lang.taxi.Decimal
type WeightInOunces inherits lang.taxi.Decimal
type OuncesToGramsMultiplier inherits lang.taxi.Decimal

model Person {
   birthDate : DateOfBirth?( @format = "dd-mm-yyyy" )
   timeOfBirth : TimeOfBirth?( @format = "hh:mm:ss" )
   startupTime : BirthTimestamp?  by (this.birthDate + this.timeOfBirth)
   weightInGrams : WeightInGrams?  by xpath("/foo")
   weightInOunces : WeightInOunces? as (WeightInGrams * OuncesToGramsMultiplier )
}
"""
      generated.trimNewLines().should.equal(expected.trimNewLines())
      generated.shouldCompile()
   }

}




private fun String.shouldCompile() {
   val errors = Compiler(this).validate()
      .filter { it.severity == Severity.ERROR }
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

package lang.taxi.generators

import com.winterbe.expekt.should
import lang.taxi.Compiler
import lang.taxi.messages.Severity
import lang.taxi.testing.shouldCompileTheSameAs
import org.junit.jupiter.api.Test
import kotlin.test.fail

// Note - this is also more heavily tested via the Kotlin to Taxi projects.
class SchemaWriterTest {
   @Test
   fun generatesAnnotationsOnFields() {
      val src = Compiler(
         """
         model Person {
            @Id
            firstName : String
         }
      """.trimIndent()
      ).compile()
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
      val src = Compiler(
         """namespace foo {
type Person
}""".trimIndent()
      ).compile()
      val generated = SchemaWriter().generateSchemas(listOf(src))[0]
      val expected = """namespace foo {
type Person
}"""
      generated.trimNewLines().should.equal(expected.trimNewLines())
   }

   @Test
   fun outputsFormatsOnTypes() {
      val src = Compiler(
         """
         type DateOfBirth inherits Date
         type TimeOfBirth inherits Time
         type BirthTimestamp inherits Instant
         type WeightInGrams inherits Decimal
         type WeightInOunces inherits Decimal
         type OuncesToGramsMultiplier inherits Decimal
         model Person {
            @Format( "dd-mm-yyyy" )
            birthDate : DateOfBirth
            @Format( "hh:mm:ss" )
            timeOfBirth : TimeOfBirth
            startupTime : BirthTimestamp by this.birthDate + this.timeOfBirth
            weightInGrams : WeightInGrams by xpath("/foo")
            weightInOunces : WeightInOunces by WeightInGrams * OuncesToGramsMultiplier
         }
      """.trimIndent()
      ).compile()
      val generated = SchemaWriter().generateSchemas(listOf(src))[0]
      val expected = """type DateOfBirth inherits Date
type TimeOfBirth inherits Time
type BirthTimestamp inherits Instant
type WeightInGrams inherits Decimal
type WeightInOunces inherits Decimal
type OuncesToGramsMultiplier inherits Decimal

model Person {
   @Format( "dd-mm-yyyy" )
   birthDate : DateOfBirth
   @Format( "hh:mm:ss" )
   timeOfBirth : TimeOfBirth
   startupTime : BirthTimestamp  by this.birthDate + this.timeOfBirth
   weightInGrams : WeightInGrams  by xpath("/foo")
   weightInOunces : WeightInOunces  by WeightInGrams * OuncesToGramsMultiplier
}
"""
      generated.shouldCompileTheSameAs(expected)
   }

   @Test
   fun `outputs default values`() {
      val src = Compiler(
         """
         type StringField inherits String
         type NumberField inherits Int
         model Thing {
            stringThing : StringField by default("foo")
            numberThing : NumberField by default(2)
         }
      """.trimIndent()
      ).compile()
      val generated = SchemaWriter().generateSchemas(listOf(src))[0]
      val expected = """
         type StringField inherits String

         type NumberField inherits Int

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
                @Format( "yyyy-MM-dd'T'HH:mm:ss.SSSSSSS")
                @Format("yyyy-MM-dd'T'HH:mm:ss.SSS")
                orderDateTime : TransactionEventDateTime
            }"""
      val schema = Compiler(src).compile()
      val generated = SchemaWriter().generateSchemas(listOf(schema))[0]
      val expected = """
         type TransactionEventDateTime inherits Instant

         model Order {
            @Format( "yyyy-MM-dd'T'HH:mm:ss.SSSSSSS")
            @Format( "yyyy-MM-dd'T'HH:mm:ss.SSS" )
            orderDateTime : TransactionEventDateTime
         }
      """.trimIndent()
      generated.shouldCompileTheSameAs(expected)
   }

   @Test
   fun `outputs multiple formats with offset`() {
      val src = """type TransactionEventDateTime inherits Instant
            model Order {
                @Format("yyyy-MM-dd'T'HH:mm:ss.SSSSSSS")
                 @Format( value = "yyyy-MM-dd'T'HH:mm:ss.SSS", offset = 60)
                orderDateTime : TransactionEventDateTime
            }"""
      val schema = Compiler(src).compile()
      val generated = SchemaWriter().generateSchemas(listOf(schema))[0]
      val expected = """type TransactionEventDateTime inherits Instant
      model Order {
         @Format( "yyyy-MM-dd'T'HH:mm:ss.SSSSSSS")
          @Format(value = "yyyy-MM-dd'T'HH:mm:ss.SSS", offset = 60 )
         orderDateTime : TransactionEventDateTime
      }""".trimIndent()
      generated.shouldCompileTheSameAs(expected)
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
                  qty1: Qty inherits Decimal
                  qty2: QtyHit inherits Decimal
                  field1: SomeAnotherQty by coalesce(this.qty1, this.qty2)
               }

            """.trimIndent()
         .compileAndRegenerateSource()

      val expected = """
        type Qty inherits Decimal

type QtyHit inherits Decimal

type QtyFill inherits Decimal

type SomeQty inherits Decimal

type SomeAnotherQty inherits SomeQty

model Foo {
   qty1 : Qty
   qty2 : QtyHit
   field1 : SomeAnotherQty  by coalesce(this.qty1, this.qty2)
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
                this.initialQuantity == this.leavesQuantity -> "ZeroFill"
                this.trader == "Marty" || this.status == "Pending" -> "ZeroFill"
                this.leavesQuantity > 0 && this.leavesQuantity < this.initialQuantity -> "PartialFill"
                this.leavesQuantity > 0 && this.leavesQuantity < this.initialQuantity || this.trader == "Marty" || this.status == "Pending"  -> "FullyFilled"
                this.leavesQuantity == null && this.initialQuantity != null -> this.trader
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
                this.initialQuantity == this.leavesQuantity -> "ZeroFill"
                this.trader == "Marty" || this.status == "Pending" -> "ZeroFill"
                this.leavesQuantity > 0 && this.leavesQuantity < this.initialQuantity -> "PartialFill"
                this.leavesQuantity > 0 && this.leavesQuantity < this.initialQuantity || this.trader == "Marty" || this.status == "Pending" -> "FullyFilled"
                this.leavesQuantity == null && this.initialQuantity != null -> this.trader
                else -> "CompleteFill"
            }
         }
      """.trimIndent()
      generated.trimNewLines().should.equal(expected.trimNewLines())
      generated.shouldCompile()
   }

   @Test
   fun outputsFormatsOnNullableTypes() {
      val src = Compiler(
         """
         type DateOfBirth inherits Date
         type TimeOfBirth inherits Time
         type BirthTimestamp inherits Instant
         type WeightInGrams inherits Decimal
         type WeightInOunces inherits Decimal
         type OuncesToGramsMultiplier inherits Decimal
         model Person {
            @Format ("dd-mm-yyyy")
            birthDate : DateOfBirth?

            @Format("hh:mm:ss" )
            timeOfBirth : TimeOfBirth?

            startupTime : BirthTimestamp? by (this.birthDate + this.timeOfBirth)
            weightInGrams : WeightInGrams? by xpath("/foo")
            weightInOunces : WeightInOunces? by (WeightInGrams * OuncesToGramsMultiplier)
         }
      """.trimIndent()
      ).compile()
      val generated = SchemaWriter().generateSchemas(listOf(src))[0]
      val expected = """type DateOfBirth inherits Date
type TimeOfBirth inherits Time
type BirthTimestamp inherits Instant
type WeightInGrams inherits Decimal
type WeightInOunces inherits Decimal
type OuncesToGramsMultiplier inherits Decimal

model Person {
   @taxi.stdlib.Format(value = "dd-mm-yyyy" )
   birthDate : DateOfBirth?

   @taxi.stdlib.Format(value = "hh:mm:ss" )
   timeOfBirth : TimeOfBirth?
   startupTime : BirthTimestamp?  by this.birthDate + this.timeOfBirth
   weightInGrams : WeightInGrams?  by xpath("/foo")
   weightInOunces : WeightInOunces?  by WeightInGrams * OuncesToGramsMultiplier
}
"""
      generated.shouldCompileTheSameAs(expected)
//      generated.trimNewLines().should.equal(expected.trimNewLines())
//      generated.shouldCompile()
   }

   @Test
   fun `outputs annotation on model`() {
      val src = Compiler(
         """
         annotation Generated {
            name : String
         }

         @Generated(name = "Compiler")
         model Person {
            firstName : String
         }
      """.trimIndent()
      ).compile()
      val generated = SchemaWriter().generateSchemas(listOf(src))[0]
      val expected = """
annotation Generated {
   name : lang.taxi.String
}

@Generated(name = "Compiler")
model Person {
   firstName : String
}
"""
      generated.shouldCompileTheSameAs(expected)
   }

   @Test
   fun `outputs annotation types`() {
      val source = Compiler("""
         @MyAnnotation
         annotation GeneratedSource {
            @MyFieldAnnotation
            generatorName : String
            anotherField : String?
         }
      """).compile()
      val generated = SchemaWriter().generateSchemas(listOf(source))[0]
      generated.shouldCompile()
      val expected = """
@MyAnnotation
annotation GeneratedSource {
   @MyFieldAnnotation
   generatorName : lang.taxi.String
   anotherField : lang.taxi.String?
}
""".trimIndent()
      generated.trimNewLines().should.equal(expected.trimNewLines())
   }

   @Test
   fun `outputs complex when statements statements under a namespace`() {
      val generated = """
            namespace test {
               model ComplexWhen {
               trader: String
               status: String
               initialQuantity: Decimal
               leavesQuantity: Decimal
               quantityStatus: String by when {
                   this.initialQuantity == this.leavesQuantity -> "ZeroFill"
                   this.trader == "Marty" || this.status == "Pending" -> "ZeroFill"
                   this.leavesQuantity > 0 && this.leavesQuantity < this.initialQuantity -> "PartialFill"
                   this.leavesQuantity > 0 && this.leavesQuantity < this.initialQuantity || this.trader == "Marty" || this.status == "Pending"  -> "FullyFilled"
                   this.leavesQuantity == null && this.initialQuantity != null -> this.trader
                   else -> "CompleteFill"
               }
            }
         }
            """.trimIndent()
         .compileAndRegenerateSource()

      val expected = """
         namespace test {
           model ComplexWhen {
               trader : String
               status : String
               initialQuantity : Decimal
               leavesQuantity : Decimal
               quantityStatus : String  by when {
                   this.initialQuantity == this.leavesQuantity -> "ZeroFill"
                   this.trader == "Marty" || this.status == "Pending" -> "ZeroFill"
                   this.leavesQuantity > 0 && this.leavesQuantity < this.initialQuantity -> "PartialFill"
                   this.leavesQuantity > 0 && this.leavesQuantity < this.initialQuantity || this.trader == "Marty" || this.status == "Pending" -> "FullyFilled"
                   this.leavesQuantity == null && this.initialQuantity != null -> this.trader
                   else -> "CompleteFill"
               }
            }
         }
      """.trimIndent()
      generated.shouldCompileTheSameAs(expected)
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

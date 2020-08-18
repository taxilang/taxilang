package lang.taxi.generators

import com.winterbe.expekt.should
import lang.taxi.Compiler
import org.junit.Test

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
   startupTime : BirthTimestamp  by ( this.birthDate + this.timeOfBirth )
   weightInGrams : WeightInGrams  by xpath("/foo")
   weightInOunces : WeightInOunces as (WeightInGrams * OuncesToGramsMultiplier )
}
"""
      generated.trimNewLines().should.equal(expected.trimNewLines())
   }
}


fun String.trimNewLines(): String {
   return this
      .lines()
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .joinToString("")
}

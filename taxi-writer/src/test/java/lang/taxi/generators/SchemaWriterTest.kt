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
      val expected = """type Person {
   }"""
      generated.trimNewLines().should.equal(expected.trimNewLines())
   }

   @Test
   fun `types with namespace generate namespace declaration`() {
      val src = Compiler("""namespace foo {
type Person
}""".trimIndent()).compile()
      val generated = SchemaWriter().generateSchemas(listOf(src))[0]
      val expected = """namespace foo {
type Person {
   }
}"""
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

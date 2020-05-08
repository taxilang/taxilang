package lang.taxi

import com.winterbe.expekt.should
import lang.taxi.types.PrimitiveType
import org.junit.Test

class EnumTest {

   @Test
   fun enumsWithContentOfIntHaveContentParsedCorrectly() {
      val src = """
enum Foo {
   One(1),
   Two(2)
}
      """.trimIndent()
      Compiler(src).compile().enumType("Foo").value("One").value.should.equal(1)
      Compiler(src).compile().enumType("Foo").value("Two").value.should.equal(2)
   }

   @Test
   fun enumsWithContentOfStringHaveContentParsedCorrectly() {
      val src = """
enum Foo {
   One("A"),
   Two("B")
}
      """.trimIndent()
      Compiler(src).compile().enumType("Foo").value("One").value.should.equal("A")
      Compiler(src).compile().enumType("Foo").value("Two").value.should.equal("B")
   }

   @Test
   fun enumsWithoutContentShouldUseTheirNameAsTheValue() {
      val src = """
enum Foo {
   One,
   Two
}
      """.trimIndent()
      Compiler(src).compile().enumType("Foo").value("One").value.should.equal("One")
      Compiler(src).compile().enumType("Foo").value("Two").value.should.equal("Two")
   }



   @Test
   fun enumsWithContentOfIntsHaveTypeInferred() {
      val src = """
enum Foo {
   One(1),
   Two(2)
}
      """.trimIndent()
      Compiler(src).compile().enumType("Foo").basePrimitive.should.equal(PrimitiveType.INTEGER)
   }

   @Test
   fun enumsWithContentOfStringHaveTypeInferred() {
      val src = """
enum Foo {
   ONE('One'),
   TWO("Two") // Intentionally mixing the character declaration
}"""
      Compiler(src).compile().enumType("Foo").basePrimitive.should.equal(PrimitiveType.STRING)
   }

   @Test
   fun enumsWithMixedContentUseStringType() {
      val src = """
enum Foo {
   One('One'),
   TWO(2)
}"""
      Compiler(src).compile().enumType("Foo").basePrimitive.should.equal(PrimitiveType.STRING)
   }

   @Test
   fun canAddDocsThroughExtension() {
      var src = """
enum Foo {
   One,
   Two
}
[[ A foo ]]
enum extension Foo {
 [[ One foo ]]
 One,
 [[ Two foos ]]
 Two
}
""".trimIndent()
      Compiler(src).compile().enumType("Foo").value("One").typeDoc.should.equal("One foo")
   }

   @Test
   fun canDeclareSynonymBetweenTwoEnums() {
      val src = """

      """.trimIndent()
   }

}

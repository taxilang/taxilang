package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import lang.taxi.types.annotation

class MultiLineStringsSpec : DescribeSpec({

   describe("Multi-line strings") {
      val TRIPLE_QUOTE = "\"\"\""
      it("should allow an annotation with multi-line strings") {
         val annotation = """
            |annotation Foo {
            |  message : String
            |}
            |
            |@Foo(message = ${TRIPLE_QUOTE}Jimmy is
            |the "absolute"
            |best${TRIPLE_QUOTE})
            |type Name inherits String
         """.trimMargin()
            .compiled()
            .type("Name")
            .annotation("Foo")!!
         annotation.parameters["message"]!!.shouldBe("""Jimmy is
            |the "absolute"
            |best""".trimMargin())
      }


      it("should handle empty multi-line strings") {
         val annotation = """
            |annotation Foo {
            |  message : String
            |}
            |
            |@Foo(message = ${TRIPLE_QUOTE}${TRIPLE_QUOTE})
            |type Name inherits String
         """.trimMargin()
            .compiled()
            .type("Name")
            .annotation("Foo")!!
         annotation.parameters["message"]!!.shouldBe("")
      }

      it("should preserve single and double quotes within multi-line strings") {
         val annotation = """
            |annotation Foo {
            |  message : String
            |}
            |
            |@Foo(message = ${TRIPLE_QUOTE}He said "Hello 'world'" loudly${TRIPLE_QUOTE})
            |type Name inherits String
         """.trimMargin()
            .compiled()
            .type("Name")
            .annotation("Foo")!!
         annotation.parameters["message"]!!.shouldBe("""He said "Hello 'world'" loudly""")
      }
      // Edge case: Test that regular strings still work
      it("should not break existing single and double quoted strings") {
         val annotation = """
            |annotation Test {
            |  single : String
            |  double : String
            |}
            |
            |@Test(
            |  single = 'Still works',
            |  double = "Also works"
            |)
            |type Name inherits String
         """.trimMargin()
            .compiled()
            .type("Name")
            .annotation("Test")!!

         annotation.parameters["single"]!!.shouldBe("Still works")
         annotation.parameters["double"]!!.shouldBe("Also works")
      }
   }
})

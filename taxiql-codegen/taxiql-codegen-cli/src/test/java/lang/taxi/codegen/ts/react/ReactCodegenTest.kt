package lang.taxi.codegen.ts.react

import io.kotest.core.spec.style.DescribeSpec
import lang.taxi.codegen.testProjectWith
import lang.taxi.codegen.testFileOf
import lang.taxi.utils.glob

class ReactCodegenTest : DescribeSpec({

   describe("react") {
      it("should generate expected for named query in file") {
         val codeGenerator = ReactCodegen()
         val (projectDir, schema) = testProjectWith(
            schema = """
type Title inherits String
type FilmId inherits String

type ReviewScore inherits Int
type ReviewText inherits String

model Film {
   id : FilmId
   title : Title
}
            """.trimIndent(),
            testFileOf(
               "films.taxi", """
query FindFilmsWithReview {
   find { Film[] } as {
      title : Title
      reviewScore : ReviewScore
      review : ReviewText
   }[]
}""".trimIndent()
            )
         )
         codeGenerator.generate(projectDir.glob("**/*.taxi"), schema, emptyList())
      }

   }
})



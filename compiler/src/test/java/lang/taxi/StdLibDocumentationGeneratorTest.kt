package lang.taxi

import com.google.common.io.Resources
import io.kotest.core.spec.style.DescribeSpec
import lang.taxi.functions.stdlib.Collections
import lang.taxi.functions.stdlib.Functional
import lang.taxi.functions.stdlib.Strings
import lang.taxi.functions.vyne.aggregations.Aggregations
import lang.taxi.utils.log
import java.nio.file.Path
import java.nio.file.Paths


class StdLibDocumentationGeneratorTest : DescribeSpec({

   it("generates docs for the stdlib") {
//      Strings.functions +
//      Aggregations.functions +
//      Functional.functions +
//      Collections.functions +
      var template = Resources.getResource("stdlib.mdx")
         .readText()
      val generatedHeaderWarning = """---
         |IMPORTANT: This file is generated.  Do not edit manually.  For the preamble, edit stdlib.mdx in compiler/src/test/resource. All other content is generated directly from classes
         |
      """.trimMargin()
      template = template.replaceFirst("---", generatedHeaderWarning)

      val schema = """""".compiled()

      val docs = TypeDocDocumentationWriter(schema)
         .appendSection("Strings", "A collection of functions for manipulating strings", Strings.functions)
         .appendSection("Collections", "A collection of functions for operating on collections", Collections.functions)
//         .appendSection("Aggregations", "Functions for aggregating data.", Aggregations.functions)
         .appendSection("Functional", "Functions that are functionally functions. Funky", Functional.functions)
         .generate()

      val file = docPath("stdlib.mdx").toFile()
      file.writeText(template + docs)
      log().info("Wrote pipeline spec documentation to ${file.absolutePath}")
   }




})

private fun docPath(fileName: String): Path {
   val currentPath = Paths.get(".").toAbsolutePath()
   val compilerPathIndex = currentPath
      .indexOf(Paths.get("compiler"))
   // Returns the root of the project
   val projectPart = currentPath.subpath(0, compilerPathIndex).toString()
   val projectRootPath = Paths.get("/", projectPart)
   val docsPath = projectRootPath.resolve("docs/source/language-reference/")
   return docsPath.resolve(fileName)
}

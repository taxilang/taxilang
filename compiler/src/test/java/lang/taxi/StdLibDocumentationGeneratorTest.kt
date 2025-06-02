package lang.taxi

import com.google.common.io.Resources
import io.kotest.core.spec.style.DescribeSpec
import lang.taxi.functions.stdlib.Collections
import lang.taxi.functions.stdlib.Dates
import lang.taxi.functions.stdlib.EnumFunctions
import lang.taxi.functions.stdlib.Errors
import lang.taxi.functions.stdlib.DocsSnippet
import lang.taxi.functions.stdlib.FunctionApi
import lang.taxi.functions.stdlib.Functional
import lang.taxi.functions.stdlib.Math
import lang.taxi.functions.stdlib.HasRunnableExamples
import lang.taxi.functions.stdlib.ObjectFunctions
import lang.taxi.functions.stdlib.StdLib
import lang.taxi.functions.stdlib.Strings
import lang.taxi.functions.stdlib.Transformations
import lang.taxi.functions.vyne.aggregations.Aggregations
import lang.taxi.utils.log
import java.nio.file.Path
import java.nio.file.Paths


class StdLibDocumentationGeneratorTest : DescribeSpec({

   it("has working examples") {
      val runnableExamples: List<Pair<String, List<DocsSnippet>>> =  StdLib.functions.filterIsInstance<HasRunnableExamples>()
         .map {
            val function = it as FunctionApi
            val functionName = function.name.fullyQualifiedName
            functionName to it.examples
         }

      // TODO :
      // For each runnable example, we need to submit it to the Taxi query endpoint, which will return JSON.
      // Verify that the returned JSON matches the expected json (using a JSON matcher, not using string equivalence)
   }

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
         .appendSection("Dates", "Mess about with time. Flux capacitor not included", Dates.functions)
         .appendSection("Math", "Numbers 'n' such. Maths for the brits.", Math.functions)
         .appendSection("Objects", "Utilities for dealing with equality, etc", ObjectFunctions.functions)
         .appendSection("Enums", "Utilities for enums", EnumFunctions.functions)
         .appendSection("Aggregations", "Functions for aggregating data within transformations.", Aggregations.functions)
         .appendSection("Functional", "Functions that are functionally functions. Funky", Functional.functions)
         .appendSection("Transformations", "Functions for converting between types", Transformations.functions)
         .appendSection("Errors", "Functions for creating and handling errors", Errors.functions)
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

   val docsPath = projectRootPath.resolve("docs2.0/src/pages/docs/language/")
   return docsPath.resolve(fileName)
}

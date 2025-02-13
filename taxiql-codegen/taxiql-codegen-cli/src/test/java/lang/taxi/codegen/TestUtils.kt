package lang.taxi.codegen

import io.kotest.core.TestConfiguration
import io.kotest.engine.spec.tempdir
import lang.taxi.Compiler
import lang.taxi.TaxiDocument
import java.io.File

data class TestFile(val name: String, val content: String)
fun testFileOf(name: String, content: String) = TestFile(name, content)

fun TestConfiguration.testProjectWith(
   schema: String,
   vararg files: TestFile
): Pair<File, TaxiDocument> {
   val dir = tempdir()
   files.forEach { testFile ->
      dir.resolve(testFile.name)
         .writeText(testFile.content)
   }
   val taxiDoc = Compiler.forStrings(schema).compile()
   return dir to taxiDoc
}

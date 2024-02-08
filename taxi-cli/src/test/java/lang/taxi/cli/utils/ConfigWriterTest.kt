package lang.taxi.cli.utils

import com.winterbe.expekt.should
import lang.taxi.packages.TaxiPackageProject
import lang.taxi.writers.ConfigWriter
import org.junit.jupiter.api.Test

class ConfigWriterTest {

   @Test
   fun generatesExpectedConfig() {
      val project = TaxiPackageProject("foo/bar", version = "0.1.0", sourceRoot = "src/")
      val output = ConfigWriter().writeMinimal(project)
      val expected = """name: foo/bar
version: 0.1.0
sourceRoot: src/
additionalSources: {}
dependencies: {}
"""
      output.should.equal(expected)
   }
}

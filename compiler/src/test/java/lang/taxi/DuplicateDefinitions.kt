package lang.taxi

import com.google.common.io.Resources
import lang.taxi.packages.TaxiSourcesLoader
import org.junit.Ignore
import org.junit.Test
import java.nio.file.Paths

class DuplicateDefinitions {
   @Ignore("Need to make this work consistently. See TokenCollator:collectDuplicateTypes for detail")
   @Test(expected = CompilationException::class)
   fun `Duplicate type definitions`() {
      val root = Resources.getResource("duplicate-definitions").toURI()
      val taxiProject = TaxiSourcesLoader.loadPackage(Paths.get(root))
      val doc = Compiler(taxiProject).compile()
   }
}

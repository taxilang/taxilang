package lang.taxi

import com.google.common.io.Resources
import com.winterbe.expekt.should
import lang.taxi.packages.TaxiSourcesLoader
import org.junit.Test
import java.nio.file.Paths

class DuplicateDefinitions {
   @Test(expected = CompilationException::class)
   fun `Duplicate type definitions`() {
      val root = Resources.getResource("duplicate-definitions").toURI()
      val taxiProject = TaxiSourcesLoader.loadPackage(Paths.get(root))
      val doc = Compiler(taxiProject).compile()
   }
}

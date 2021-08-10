package lang.taxi

import com.google.common.io.Resources
import lang.taxi.packages.TaxiSourcesLoader
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Paths

class DuplicateDefinitions {
   @Disabled("Need to make this work consistently. See TokenCollator:collectDuplicateTypes for detail")
   @Test
   fun `Duplicate type definitions`() {
      val root = Resources.getResource("duplicate-definitions").toURI()
      val taxiProject = TaxiSourcesLoader.loadPackage(Paths.get(root))
      assertThrows<CompilationException> {
         Compiler(taxiProject).compile()
      }
   }
}

package lang.taxi

import com.google.common.io.Resources
import com.winterbe.expekt.should
import lang.taxi.packages.TaxiSourcesLoader
import org.junit.jupiter.api.Test
import java.nio.file.Paths

class TaxiProjectTest {
   @Test
   fun canLoadFromTaxiProject() {
      val root = Resources.getResource("sample-project").toURI()
      val taxiProject = TaxiSourcesLoader.loadPackage(Paths.get(root))
      val doc = Compiler(taxiProject).compile()
      doc.types.should.have.size(9)

   }
}

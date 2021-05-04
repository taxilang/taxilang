package lang.taxi

import com.google.common.io.Resources
import com.winterbe.expekt.should
import lang.taxi.messages.Severity
import lang.taxi.packages.TaxiSourcesLoader
import org.junit.Test
import java.nio.file.Paths

class FpmlSpecTest {
   @Test
   fun `it should compile the generated fpml spec`() {
      val root = Resources.getResource("fpml").toURI()
      val taxiProject = TaxiSourcesLoader.loadPackage(Paths.get(root))
      val (messages,doc) = Compiler(taxiProject).compileWithMessages()
      messages.filter { it.severity == Severity.ERROR }.should.be.empty
      doc.types.should.have.size(570)
   }
}

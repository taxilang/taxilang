package lang.taxi.packages

import com.google.common.io.Resources
import com.winterbe.expekt.should
import org.junit.Test
import java.nio.file.Paths

class TaxiProjectLoaderTest {
   @Test
   fun canLoadTaxiFile() {
      val resource = Resources.getResource("testRepo/taxi/lang.taxi.Dummy/0.2.0/taxi.conf").toURI()
      val config = TaxiProjectLoader().withConfigFileAt(Paths.get(resource)).load()
      config.identifier.id.should.equal("taxi/Demo/0.2.0")
      config.dependencies["taxi/Another"].should.equal("0.3.0")
   }
}

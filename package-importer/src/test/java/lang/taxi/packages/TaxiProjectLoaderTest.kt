package lang.taxi.packages

import com.google.common.io.Resources
import com.winterbe.expekt.should
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.SystemUtils
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.io.File
import java.net.URI
import java.nio.file.Paths

class TaxiProjectLoaderTest {
   @Test
   fun canLoadTaxiFile() {
      val resource = Resources.getResource("testRepo/taxi/lang.taxi.Dummy/0.2.0/taxi.conf").toURI()
      val config = TaxiProjectLoader().withConfigFileAt(Paths.get(resource)).load()
      config.identifier.id.should.equal("taxi/Demo/0.2.0")
      config.dependencies["taxi/Another"].should.equal("0.3.0")
   }

   @Ignore
   @Test
   fun taxiProjectLoaderBug() {
      File(Resources.getResource("testRepo/taxi/lang.taxi.Dummy/0.2.0/anotherConfig/taxi.conf").toURI())
         .copyTo(File("${SystemUtils.getUserHome()}/.taxi/taxi.conf"), true)
      val anotherConfig = File("${SystemUtils.getUserHome()}/.taxi/taxi.conf")

      val resource = Resources.getResource("testRepo/taxi/lang.taxi.Dummy/0.2.0/taxi.conf").toURI()
      val config = TaxiProjectLoader().withConfigFileAt(Paths.get(resource)).load()
      config.identifier.id.should.equal("taxi/Demo/0.2.0")
      config.dependencies["taxi/Another"].should.equal("0.3.0")

      anotherConfig.delete()
   }
}

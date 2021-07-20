package lang.taxi.cli.config

import com.google.common.io.Resources
import com.winterbe.expekt.should
import org.junit.jupiter.api.Test
import java.nio.file.Paths


class TaxiProjectLoaderTest {

   @Test
   fun `when merging configs then both sets of settings are merged`() {
      val project = TaxiProjectLoader.noDefaults()
         .withConfigFileAt(Paths.get(Resources.getResource("samples/merging/folderA/taxi.conf").toURI()))
         .withConfigFileAt(Paths.get(Resources.getResource("samples/merging/folderB/taxi.conf").toURI()))
         .load()
      project.credentials.should.have.size(1)
      project.repositories.should.have.size(1)

      // Test deserialzation of repository properties
      project.publishToRepository!!.settings["repositoryName"].should.equal("taxi")
   }
}

package lang.taxi.cli.config

import com.google.common.io.Resources
import com.winterbe.expekt.should
import org.junit.jupiter.api.Test
import java.nio.file.Paths


// Use a TaxiSourcesLoader instead
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

   @Test
   fun `additional sources are available`() {
      val project = TaxiProjectLoader.noDefaults()
         .withConfigFileAt(Paths.get(Resources.getResource("samples/otherSources/taxi.conf").toURI()))
         .load()
      project.additionalSources.should.not.be.empty

//      should.be.equal(mapOf("@orbital/pipelines" to Paths.get("pipelines/")))
   }
}

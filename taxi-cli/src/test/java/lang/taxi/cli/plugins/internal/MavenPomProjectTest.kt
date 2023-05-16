package lang.taxi.cli.plugins.internal

import com.google.common.io.Resources
import com.winterbe.expekt.should
import org.apache.commons.io.FileUtils
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.info.BuildProperties
import java.io.File
import java.io.FileReader
import java.util.*

class MavenPomProjectTest {

   @TempDir
   @JvmField
   var folder: File? = null

   private fun copyProject(path: String) {
      val testProject = File(Resources.getResource(path).toURI())
      FileUtils.copyDirectory(testProject, folder!!)
   }

   @Test
   fun generateMavenProject() {
      copyProject("samples/maven")
      executeBuild(folder!!.toPath(), listOf(KotlinPlugin(BuildProperties(Properties()))))

      val model = loadMavenModel()

      model.repositories.should.have.size(3)
      val internalRepo = model.repositories.first { it.id == "internal-repo" }
      internalRepo.releases.isEnabled.should.be.`true`
      internalRepo.snapshots.isEnabled.should.be.`true`
      internalRepo.url.should.equal("https://newcorp.nexus.com")

      model.distributionManagement.repository.id.should.equal("some-internal-repo")
      model.distributionManagement.repository.url.should.equal("https://our-internal-repo")

      assert(this.folder!!.exists())
      assert(folder!!.list().contains("taxi.conf"))
      assert(folder!!.list().contains("src"))
      assert(folder!!.list().contains("dist"))
      assert(File(folder!!.absolutePath, "dist").list().contains("pom.xml"))
   }


   @Test
   fun usesFixedTaxiVersion() {
      copyProject("samples/maven-fixed-version")
      executeBuild(folder!!.toPath(), listOf(KotlinPlugin(BuildProperties(Properties()))))
      val model = loadMavenModel()

      val taxiDependency = model.dependencies.first { it.groupId == "org.taxilang" }
      taxiDependency.version.should.equal("0.5.0")
   }

   private fun loadMavenModel(): Model {
      val pom = File(folder!!.absolutePath, "dist/pom.xml")
      val mvnReader = MavenXpp3Reader()
      val reader = FileReader(pom)
      var model = Model()
      model = mvnReader.read(reader)
      model.pomFile = pom
      return model
   }


}

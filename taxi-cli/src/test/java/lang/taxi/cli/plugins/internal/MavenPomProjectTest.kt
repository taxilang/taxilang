package lang.taxi.cli.plugins.internal

import com.google.common.io.Resources
import com.winterbe.expekt.should
import lang.taxi.cli.commands.BuildCommand
import lang.taxi.cli.config.CliTaxiEnvironment
import lang.taxi.cli.config.TaxiProjectLoader
import lang.taxi.cli.pluginArtifacts
import lang.taxi.cli.plugins.PluginRegistry
import org.apache.commons.io.FileUtils
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.springframework.boot.info.BuildProperties
import java.io.File
import java.io.FileReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

class MavenPomProjectTest {

   @Rule
   @JvmField
   val folder = TemporaryFolder()

   @Before
   fun deployTestRepo() {

   }

   private fun copyProject(path: String) {
      val testProject = File(Resources.getResource(path).toURI())
      FileUtils.copyDirectory(testProject, folder.root)
   }

   @Test
   fun generateMavenProject() {
      copyProject("samples/maven")
      executeBuild()

      val model = loadMavenModel()

      model.repositories.should.have.size(3)
      val internalRepo = model.repositories.first { it.id == "internal-repo" }
      internalRepo.releases.isEnabled.should.be.`true`
      internalRepo.snapshots.isEnabled.should.be.`true`
      internalRepo.url.should.equal("https://newcorp.nexus.com")

      model.distributionManagement.repository.id.should.equal("some-internal-repo")
      model.distributionManagement.repository.url.should.equal("https://our-internal-repo")

      assert(this.folder.root.exists())
      assert(folder.root.list().contains("taxi.conf"))
      assert(folder.root.list().contains("src"))
      assert(folder.root.list().contains("dist"))
      assert(File(folder.root.absolutePath, "dist").list().contains("pom.xml"))
   }



   @Test
   fun usesFixedTaxiVersion() {
      copyProject("samples/maven-fixed-version")
      executeBuild()
      val model = loadMavenModel()

      val taxiDependency = model.dependencies.first { it.groupId == "lang.taxi" }
      taxiDependency.version.should.equal("0.5.0")
   }


   private fun executeBuild() {
      val project = TaxiProjectLoader().withConfigFileAt(folder.root.toPath().resolve("taxi.conf")).load()
      val build = BuildCommand(PluginRegistry(
         internalPlugins = listOf(KotlinPlugin(BuildProperties(Properties()))),
         requiredPlugins = project.pluginArtifacts()
      ))
      val environment = CliTaxiEnvironment.forRoot(folder.root.toPath(), project)
      build.execute(environment)
   }

   private fun loadMavenModel(): Model {
      val pom = File(folder.root.absolutePath, "dist/pom.xml")
      val mvnReader = MavenXpp3Reader()
      val reader = FileReader(pom)
      var model = Model()
      model = mvnReader.read(reader)
      model.pomFile = pom
      return model
   }


}

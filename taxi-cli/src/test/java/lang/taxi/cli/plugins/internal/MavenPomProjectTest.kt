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
      val testProject = File(Resources.getResource("samples/maven").toURI())
      FileUtils.copyDirectory(testProject, folder.root)
   }

   @Test
   fun generateMavenProject() {
      val project = TaxiProjectLoader().withConfigFileAt(folder.root.toPath().resolve("taxi.conf")).load()
      val pom = File(folder.root.absolutePath, "dist/pom.xml")
      var model = Model()
      val mvnReader = MavenXpp3Reader()
      val build = BuildCommand(PluginRegistry(
         internalPlugins = listOf(KotlinPlugin(BuildProperties(Properties()))),
         requiredPlugins = project.pluginArtifacts()
      ))
      val environment = CliTaxiEnvironment.forRoot(folder.root.toPath(), project)
      build.execute(environment)

      val reader = FileReader(pom)
      model = mvnReader.read(reader)
      model.pomFile = pom

      model.repositories.should.have.size(3)
      val internalRepo = model.repositories.first { it.id == "internal-repo" }
      internalRepo.releases.isEnabled.should.be.`true`
      internalRepo.snapshots.isEnabled.should.be.`true`
      internalRepo.url.should.equal("https://newcorp.nexus.com")
      
      assert(this.folder.root.exists())
      assert(folder.root.list().contains("taxi.conf"))
      assert(folder.root.list().contains("src"))
      assert(folder.root.list().contains("dist"))
      assert(File(folder.root.absolutePath, "dist").list().contains("pom.xml"))
   }

}

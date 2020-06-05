package lang.taxi.cli.plugins.internal

import com.google.common.io.Resources
import lang.taxi.cli.commands.BuildCommand
import lang.taxi.cli.config.CliTaxiEnvironment
import lang.taxi.cli.config.TaxiProjectLoader
import lang.taxi.cli.pluginArtifacts
import lang.taxi.cli.plugins.PluginRegistry
import org.apache.commons.io.FileUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

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
      val build = BuildCommand(PluginRegistry(
         internalPlugins = listOf(KotlinPlugin()),
         requiredPlugins = project.pluginArtifacts()
      ))
      val environment = CliTaxiEnvironment.forRoot(folder.root.toPath(), project)
      build.execute(environment)

      assert(this.folder.root.exists())
      assert(folder.root.list().contains("taxi.conf"))
      assert(folder.root.list().contains("src"))
      assert(folder.root.list().contains("dist"))
      assert(Files.exists(File("${this.folder.root.absolutePath}/dist/pom.xml").toPath()))
   }

}

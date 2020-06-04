package lang.taxi.cli.plugins.internal

import com.google.common.io.Resources
import lang.taxi.cli.commands.BuildCommand
import lang.taxi.cli.config.CliTaxiEnvironment
import lang.taxi.cli.config.TaxiProjectLoader
import lang.taxi.cli.plugins.PluginRegistry
import org.apache.commons.io.FileUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

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
   fun buildMavenProject() {
      val project = TaxiProjectLoader().withConfigFileAt(folder.root.toPath().resolve("taxi.conf")).load()
      val build = BuildCommand(PluginRegistry(
         internalPlugins = listOf(KotlinPlugin)
      ))
      val environment = CliTaxiEnvironment.forRoot(folder.root.toPath(), project)
      build.execute(environment)
   }

}

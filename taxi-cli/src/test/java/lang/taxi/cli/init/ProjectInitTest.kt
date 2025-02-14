package lang.taxi.cli.init

import io.kotest.matchers.paths.shouldExist
import io.kotest.matchers.paths.shouldNotExist
import io.kotest.matchers.string.shouldContain
import lang.taxi.cli.commands.InitCommand
import lang.taxi.cli.config.CliTaxiEnvironment
import lang.taxi.generators.TaxiProjectEnvironment
import org.beryx.textio.TextIO
import org.beryx.textio.TextIoFactory
import org.beryx.textio.mock.MockTextTerminal
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.Charset
import java.nio.file.Path

class ProjectInitTest {
   @TempDir
   lateinit var folder: Path

   @Test
   fun `can create a project with orbital defaults`() {
      val command = InitCommand(TextIO(MockTextTerminal()))
      command.projectGroup = "com.test"
      command.projectName = "test-project"
      command.projectVersion = "1.0.0"
      command.sourceDir = "src"
      command.addOptionalConfig = true

      val environment = CliTaxiEnvironment.forRoot(folder, null)
      command.execute(environment)

      val workspaceFile = folder.resolve("workspace.conf")
      workspaceFile.shouldExist()
      workspaceFile.toFile().readText()
         .shouldContain("workspace/projects/test-project")

      // Orbital config files
      listOf(
         "orbital/config/auth.conf",
         "orbital/config/connections.conf",
         "orbital/config/env.conf",
         "orbital/config/services.conf",
         "orbital/nebula/stack.nebula.kts",
      ).forEach {
         folder.resolve("workspace/projects/test-project/$it").shouldExist()
      }
   }

   @Test
   fun `can create a project without orbital defaults`() {
      val command = InitCommand(TextIO(MockTextTerminal()))
      command.projectGroup = "com.test"
      command.projectName = "test-project"
      command.projectVersion = "1.0.0"
      command.sourceDir = "src"
      command.addOptionalConfig = false

      val environment = CliTaxiEnvironment.forRoot(folder, null)
      command.execute(environment)

      folder.resolve("workspace.conf").shouldNotExist()

      folder.resolve("taxi.conf").shouldExist()
      folder.resolve("workspace").shouldNotExist()
   }
}

package lang.taxi.cli.init

import io.kotest.matchers.paths.shouldExist
import io.kotest.matchers.paths.shouldNotExist
import io.kotest.matchers.string.shouldContain
import lang.taxi.cli.commands.InitCommand
import lang.taxi.cli.config.CliTaxiEnvironment
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import java.nio.file.Path

class ProjectInitTest {
   @TempDir
   lateinit var folder: Path

   @Test
   fun `can create a project with orbital defaults`() {
      val command = InitCommand(mock { })
      command.projectGroup = "com.test"
      command.projectName = "test-project"
      command.projectVersion = "1.0.0"
      command.sourceDir = "src"
      command.addOptionalConfig = true

      val environment = CliTaxiEnvironment.forRoot(folder, null)
      command.execute(environment)

      val workspaceFile = folder.resolve("workspace/workspace.conf")
      workspaceFile.shouldExist()
      workspaceFile.toFile().readText()
         .shouldContain("projects/test-project")

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
      val command = InitCommand(mock { })
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

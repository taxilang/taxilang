package lang.taxi.cli.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.google.common.io.Resources
import lang.taxi.cli.utils.ConfigWriter
import lang.taxi.cli.utils.log
import lang.taxi.generators.TaxiEnvironment
import lang.taxi.packages.ProjectName
import lang.taxi.packages.TaxiPackageProject
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.beryx.textio.TextIO
import org.springframework.stereotype.Component
import java.nio.file.Files

@Component
@Parameters(commandDescription = "Creates a new taxi project in the current directory")
class InitCommand(private val prompt: TextIO) : ProjectlessShellCommand {
   override val name: String = "init"

   @Parameter(
      names = ["group"],
      required = false,
      description = "The group owner of the project.  Will prompt if not provided"
   )
   lateinit var projectGroup: String

   @Parameter(names = ["name"], required = false, description = "The name of the project.  Will prompt if not provided")
   lateinit var projectName: String

   @Parameter(
      names = ["version"],
      required = false,
      description = "The version of the project.  Will prompt if not provided"
   )
   lateinit var projectVersion: String

   @Parameter(
      names = ["src"],
      required = false,
      description = "Source directory for the project, will prompt if not provided"
   )
   lateinit var sourceDir: String

   override fun execute(environment: TaxiEnvironment) {
      if (!this::projectGroup.isInitialized) {
         this.projectGroup = prompt.newStringInputReader()
            .read("Project group (eg., com.acme)")
      }
      if (!this::projectName.isInitialized) {
         this.projectName = prompt.newStringInputReader()
            .read("Project name")
      }
      if (!this::projectVersion.isInitialized) {
         this.projectVersion = prompt.newStringInputReader()
            .withDefaultValue("0.1.0")
            .read("Project version")
      }
      if (!this::sourceDir.isInitialized) {
         this.sourceDir = prompt.newStringInputReader()
            .withDefaultValue("src/")
            .read("Source directory")
      }

      log().info(
         "Creating project ${
            ProjectName(
               projectGroup,
               projectName
            ).id
         } v${projectVersion} in directory ${environment.outputPath}"
      )

      val project = TaxiPackageProject(
         name = ProjectName(projectGroup, projectName).id,
         version = projectVersion,
         sourceRoot = sourceDir
      )
      val taxiConfPath = environment.outputPath.resolve("taxi.conf")
      log().info("Writing config to $taxiConfPath")
      val config = ConfigWriter().writeMinimal(project)
      taxiConfPath.toFile().writeText(config)

      val sourcePath = environment.outputPath.resolve(sourceDir)
      if (!Files.exists(sourcePath)) {
         log().info("Generating source directory at $sourcePath")
         FileUtils.forceMkdir(sourcePath.toFile())
      }

      IOUtils.copy(
         Resources.getResource("taxi-cli-files/default-git-ignore").openStream(),
         environment.outputPath.resolve(".gitignore").toFile().outputStream()
      )

      val vscodeDir = Files.createDirectory(environment.outputPath.resolve(".vscode"))
      IOUtils.copy(
         Resources.getResource("taxi-cli-files/.vscode/extensions.json").openStream(),
         vscodeDir.resolve("extensions.json").toFile().outputStream()
      )

      log().info("Finished")
   }
}

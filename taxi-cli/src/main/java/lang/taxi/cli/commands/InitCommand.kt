package lang.taxi.cli.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.google.common.io.Resources
import lang.taxi.cli.utils.log
import lang.taxi.generators.TaxiEnvironment
import lang.taxi.packages.ProjectName
import lang.taxi.packages.TaxiPackageProject
import lang.taxi.writers.ConfigWriter
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

   @Parameter(
      names = ["includeOrbital"],
      required = false,
      description = "Add optional Orbital config (Nebula, connections.conf, and Orbital dependencies)"
   )
   var addOptionalConfig: Boolean? = null

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
      if (this.addOptionalConfig == null) {
         this.addOptionalConfig = prompt.newStringInputReader()
            .withDefaultValue("Y")
            .withInlinePossibleValues("Y","N")
            .read("Include optional Orbital config? (adds support for Nebula, connections.conf, and Orbital dependencies)")
            ?.let { value ->
               when (value.uppercase()) {
                  "Y" -> true
                  "N" -> false
                  else -> true
               }
            }
      }

      val projectPath = if(this.addOptionalConfig == true) {
         environment.outputPath.resolve("workspace/projects/$projectName")
      } else {
         environment.outputPath
      }
      projectPath.toFile().mkdirs()

      log().info(
         "Creating project ${
            ProjectName(
               projectGroup,
               projectName
            ).id
         } v${projectVersion} in directory $projectPath"
      )

      val project = TaxiPackageProject(
         name = ProjectName(projectGroup, projectName).id,
         version = projectVersion,
         sourceRoot = sourceDir
      ).let { project ->
         if (addOptionalConfig == true) {
            project.copy(
               dependencies = mapOf(
                  // TODO : How do I get this version number automatically?
                  "com.orbitalhq/core" to "github:orbitalapi/orbital-core-taxi#0.34.0"
               ),
               additionalSources = mapOf(
                  "@orbital/config" to "orbital/config/*.conf",
                  "@orbital/nebula" to "orbital/nebula/*.nebula.kts"
               )
            )
         } else project
      }

      val taxiConfPath = projectPath.resolve("taxi.conf")
      log().info("Writing config to $taxiConfPath")
      val config = ConfigWriter().writeMinimal(project)
      taxiConfPath.toFile().writeText(config)

      val sourcePath = projectPath.resolve(sourceDir)
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

      if (addOptionalConfig == true) {
         val nebulaDirectory = projectPath.resolve("orbital/nebula")
         nebulaDirectory.toFile().mkdirs()
         IOUtils.copy(
            Resources.getResource("taxi-cli-files/stack.nebula.kts").openStream(),
            nebulaDirectory.resolve("stack.nebula.kts").toFile().outputStream()
         )
         val configFiles = listOf("auth.conf", "connections.conf", "env.conf", "services.conf")
         val connectionsConfDirectory = Files.createDirectory(projectPath.resolve("orbital/config"))
         configFiles.forEach { configFilename ->
            IOUtils.copy(
               Resources.getResource("taxi-cli-files/$configFilename").openStream(),
               connectionsConfDirectory.resolve(configFilename).toFile().outputStream()
            )
         }
         val workspaceConfFile = environment.outputPath.resolve("workspace.conf").toFile()
         IOUtils.copy(
            Resources.getResource("taxi-cli-files/workspace.conf").openStream(),
            workspaceConfFile.outputStream()
         )
         val updated = workspaceConfFile.readText().replace("{PROJECT_NAME}", project.identifier.name.name)
         workspaceConfFile.writeText(updated)
      }

      log().info("Finished")
   }
}

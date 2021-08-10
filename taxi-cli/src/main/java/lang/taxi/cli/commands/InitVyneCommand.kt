package lang.taxi.cli.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.google.common.io.Resources
import lang.taxi.cli.utils.log
import lang.taxi.generators.TaxiEnvironment
import org.beryx.textio.TextIO
import org.springframework.stereotype.Component
import java.nio.file.Files


@Component
@Parameters(commandDescription = "Creates a docker-compose file to launch a local developers instance of Vyne")
class InitVyneCommand(private val prompt: TextIO) : ProjectlessShellCommand {
   override val name: String = "vyne"

   @Parameter(
      names = ["-v", "--version"],
      required = false,
      description = "The version of vyne to use.  Defaults to latest if not provided"
   )
   var vyneVersion: String = "latest"

   @Parameter(
      names = ["-f", "--force"],
      required = false,
      description = "Overwrite an existing docker-compose.yml if present.  Defaults to false."
   )
   var force: Boolean = false


   override fun execute(environment: TaxiEnvironment) {
      val dockerComposePath = environment.projectRoot.resolve("docker-compose.yml")
      if (Files.exists(dockerComposePath) && !force) {
         log().info("docker-compose.yml already exists, not recreating.  If you'd like to change the version used, use -f (or --force)")
      } else {
         val dockerComposeSource = Resources.getResource("taxi-cli-files/docker-compose.yml")
            .readText().let { dockerCompose ->
               if (vyneVersion != "latest") {
                  dockerCompose.replace(":latest", ":$vyneVersion")
               } else {
                  dockerCompose
               }
            }
         dockerComposePath.toFile().writeText(dockerComposeSource)
         log().info("Successfully created docker-compose.yml")
      }


      log().info("Pulling latest docker images")
      val processBuilder = ProcessBuilder()
      val dockerComposePull = processBuilder.directory(dockerComposePath.parent.toFile())
         .command("docker-compose", "pull")
         .inheritIO()
         .start()
         .waitFor()

      processBuilder.directory(dockerComposePath.parent.toFile())
         .command("docker-compose", "up", "-d")
         .inheritIO()
         .start()
         .waitFor()

      log().info("Finished.  Vyne is running, and watching changes in this folder.  Wait about 45 seconds, then head to http://localhost:9022")
   }
}

package lang.taxi.cli.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import lang.taxi.cli.utils.log
import lang.taxi.generators.TaxiEnvironment
import org.beryx.textio.TextIO
import org.springframework.stereotype.Component
import java.net.URI
import java.nio.file.Files


@Component
@Parameters(commandDescription = "Creates a docker-compose file to launch a local developers instance of Orbital")
class InitOrbitalCommand(private val prompt: TextIO) : ProjectlessShellCommand {
   override val name: String = "orbital"

   @Parameter(
      names = ["-v", "--version"],
      required = false,
      description = "The version of Orbital to use.  Defaults to latest if not provided"
   )
   var orbitalVersion: String = "latest"

   @Parameter(
      names = ["-f", "--force"],
      required = false,
      description = "Overwrite an existing docker-compose.yml if present.  Defaults to false."
   )
   var force: Boolean = false

   @Parameter(
      names = ["-u", "--url"],
      required = false,
      description = "Specifies the url to download the config file from.  Defaults to start.orbitalhq.com"
   )
   var url: String = "https://start.orbitalhq.com"

   override fun execute(environment: TaxiEnvironment) {
      val dockerComposePath = environment.projectRoot.resolve("docker-compose.yml")
      if (Files.exists(dockerComposePath) && !force) {
         log().info("docker-compose.yml already exists, not recreating.  If you'd like to change the version used, use -f (or --force)")
      } else {
         log().info("Downloading docker-compose.yml from $url")
         val dockerComposeSource = URI.create(url).toURL()
            .readText().let { dockerCompose ->
               if (orbitalVersion != "latest") {
                  dockerCompose.replace(":latest", ":$orbitalVersion")
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

      log().info("Finished.  Orbital is running, and watching changes in this folder.  Wait about 45 seconds, then head to http://localhost:9022")
   }
}

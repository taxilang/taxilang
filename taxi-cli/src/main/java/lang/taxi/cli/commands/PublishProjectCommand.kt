package lang.taxi.cli.commands

import com.beust.jcommander.Parameters
import lang.taxi.generators.TaxiProjectEnvironment
import lang.taxi.packages.PackagePublisher
import org.springframework.stereotype.Component

@Component
@Parameters(commandDescription = "Publishes a taxi project to a repository.")
class PublishProjectCommand : ProjectShellCommand {
   override val name: String = "publish"

   override fun execute(environment: TaxiProjectEnvironment) {
      PackagePublisher(credentials = environment.project.credentials).publish(
         environment.projectRoot
      )
   }

}

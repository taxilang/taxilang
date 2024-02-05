package lang.taxi.cli.commands

import com.beust.jcommander.Parameters
import lang.taxi.generators.TaxiProjectEnvironment
import org.springframework.stereotype.Component
import org.taxilang.packagemanager.repository.nexus.PackagePublisher

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

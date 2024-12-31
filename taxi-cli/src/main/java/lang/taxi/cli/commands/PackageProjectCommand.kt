package lang.taxi.cli.commands

import com.beust.jcommander.Parameters
import lang.taxi.cli.utils.log
import lang.taxi.generators.TaxiProjectEnvironment
import org.springframework.stereotype.Component
import org.taxilang.packagemanager.repository.nexus.PackagePublisher

@Component
@Parameters(commandDescription = "Creates a zipped package of a Taxi project, suitable for uploading or distributing.")
class PackageProjectCommand  : ProjectShellCommand {
   override val name: String = "package"

   override fun execute(environment: TaxiProjectEnvironment) {
      val publisher = PackagePublisher(credentials = environment.project.credentials)
      val (project,bundle) = publisher.createBundle(environment.projectRoot)
      log().info("Project package created at ${bundle.zip}")
   }
}

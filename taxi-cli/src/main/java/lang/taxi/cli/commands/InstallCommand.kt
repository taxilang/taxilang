package lang.taxi.cli.commands

import com.beust.jcommander.Parameters
import lang.taxi.cli.utils.log
import lang.taxi.generators.TaxiEnvironment
import lang.taxi.generators.TaxiProjectEnvironment
import lang.taxi.packages.ImporterConfig
import org.beryx.textio.TextIO
import org.springframework.stereotype.Component
import org.taxilang.packagemanager.PackageManager
import org.taxilang.packagemanager.RepositorySystemProvider

@Component
@Parameters(commandDescription = "Installs the current project (along with any dependencies) into the local repository")
class InstallCommand (private val buildCommand: BuildCommand) : ProjectShellCommand {
   override val name: String = "install"

   override fun execute(environment: TaxiProjectEnvironment) {
      log().info("Building ${environment.project.identifier.id}")
      buildCommand.execute(environment)
      log().info("Installing ${environment.project.identifier.id}")
      val packageManager = createPackageManager(environment)
      packageManager.bundleAndInstall(environment.projectRoot, environment.project)
   }

   private fun createPackageManager(environment: TaxiEnvironment): PackageManager {
      val project = environment.project!!
      val importerConfig = ImporterConfig.forProject(project)
      val (repoSystem, session) = RepositorySystemProvider.build()
      val packageManager = PackageManager(
         importerConfig,
         repoSystem,
         session,
      )
      return packageManager
   }
}

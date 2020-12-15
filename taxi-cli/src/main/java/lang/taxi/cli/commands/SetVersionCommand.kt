package lang.taxi.cli.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.github.zafarkhaja.semver.Version
import lang.taxi.cli.utils.VersionUpdater
import lang.taxi.cli.utils.log
import lang.taxi.generators.TaxiProjectEnvironment
import org.springframework.stereotype.Component

@Component
@Parameters(commandDescription = "Sets the version of the taxi project to a specific value")
class SetVersionCommand(private val versionUpdater: VersionUpdater) : ProjectShellCommand {
   @Parameter(required = true, description = "The new version of the project")
   lateinit var version: String

   override val name: String = "set-version"

   override fun execute(environment: TaxiProjectEnvironment) {
      val semVer: Version = Version.valueOf(environment.project.version)
      val newVersion: Version

      try {
         newVersion = Version.valueOf(version)
      } catch (e: Exception) {
         log().error("$version is not a valid version.").also {
            throw IllegalArgumentException("$version is not a valid version.")
         }
      }

      log().info("Version updated from ${semVer.normalVersion} to $newVersion")
      versionUpdater.write(environment.projectRoot.resolve("taxi.conf"), newVersion.normalVersion)
   }
}

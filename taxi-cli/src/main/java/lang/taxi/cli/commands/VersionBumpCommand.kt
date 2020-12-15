package lang.taxi.cli.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.github.zafarkhaja.semver.Version
import lang.taxi.cli.plugins.internal.ReleaseType
import lang.taxi.cli.utils.VersionUpdater
import lang.taxi.cli.utils.log
import lang.taxi.generators.TaxiProjectEnvironment
import org.springframework.stereotype.Component

@Component
@Parameters(commandDescription = "Updates the current version in taxi.conf using semver")
class VersionBumpCommand(private val versionUpdater: VersionUpdater) : ProjectShellCommand {
   @Parameter(required = true, description = "Version part to bump - allowable values are major, minor or patch")
   lateinit var increment: String

   override val name: String = "version-bump"

   override fun execute(environment: TaxiProjectEnvironment) {
      val semVer: Version = Version.valueOf(environment.project.version)

      val newVersion = when (ReleaseType.parse(increment)) {
         ReleaseType.MAJOR -> semVer.incrementMajorVersion().normalVersion
         ReleaseType.MINOR -> semVer.incrementMinorVersion().normalVersion
         ReleaseType.PATCH -> semVer.incrementPatchVersion().normalVersion
         else -> throw IllegalArgumentException("Undefined parameter: $increment")
      }
      log().info("Version updated from ${semVer.normalVersion} to $newVersion")
      versionUpdater.write(environment.projectRoot.resolve("taxi.conf"), newVersion)
   }
}

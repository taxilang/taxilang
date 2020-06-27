package lang.taxi.cli.commands

import com.beust.jcommander.Parameter
import com.github.zafarkhaja.semver.Version
import lang.taxi.cli.utils.VersionUpdater
import lang.taxi.cli.plugins.internal.ReleaseType
import lang.taxi.cli.utils.log
import lang.taxi.generators.TaxiEnvironment
import org.springframework.stereotype.Component

@Component
class VersionBumpCommand(private val versionUpdater: VersionUpdater) : ShellCommand {
   @Parameter(required = true, description = "major, minor or patch increment of version.")
   lateinit var increment: String

   override val name: String = "version-bump"

   override fun execute(environment: TaxiEnvironment) {
      val semVer: Version = Version.valueOf(environment.project.version)
      var newVersion = ""

      when (ReleaseType.parse(increment)) {
         ReleaseType.MAJOR -> newVersion = semVer.incrementMajorVersion().normalVersion
         ReleaseType.MINOR -> newVersion = semVer.incrementMinorVersion().normalVersion
         ReleaseType.PATCH -> newVersion = semVer.incrementPatchVersion().normalVersion
         else -> log().error("Undefined parameter: $increment")
            .also { throw IllegalArgumentException("Undefined parameter: $increment") }
      }
      log().info("Version updated from ${semVer.normalVersion} to $newVersion")
      versionUpdater.write(environment.projectRoot.resolve("taxi.conf"), newVersion)
   }
}

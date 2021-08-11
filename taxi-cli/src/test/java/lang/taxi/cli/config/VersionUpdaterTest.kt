package lang.taxi.cli.config

import com.google.common.io.Resources
import com.winterbe.expekt.should
import lang.taxi.cli.commands.SetVersionCommand
import lang.taxi.cli.commands.VersionBumpCommand
import lang.taxi.cli.utils.VersionUpdater
import lang.taxi.generators.TaxiProjectEnvironment
import lang.taxi.packages.TaxiPackageProject
import org.apache.commons.io.FileUtils
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class VersionUpdaterTest {

   @TempDir
   @JvmField
   var folder: Path? = null

   private val taxiEnvironment = object : TaxiProjectEnvironment {
      override val projectRoot: Path
         get() = folder!!
      override val outputPath: Path
         get() = folder!!
      override val project: TaxiPackageProject
         get() = TaxiPackageProject("taxi/taxi", "1.1.1")
   }

   lateinit var taxiConf: File

   @BeforeEach
   fun deployTestRepo() {
      val sampleTaxiConf = File(Resources.getResource("samples/maven/taxi.conf").toURI())
      taxiConf = folder!!.toFile().resolve("taxi.conf")
      FileUtils.copyFile(sampleTaxiConf, taxiConf)
   }

   @Test
   fun canWriteConfigFile() {
      val project = TaxiProjectLoader().withConfigFileAt(taxiConf.toPath()).load()
      val updated = project.copy(version = "0.4.0")
      VersionUpdater().write(taxiConf.toPath(), updated.version)

      // re-read the sources
      val reloaded = TaxiProjectLoader().withConfigFileAt(taxiConf.toPath()).load()
      reloaded.version.should.equal("0.4.0")

   }

   @Test
   fun canIncrementVersion() {
      val versionBumpCommand = VersionBumpCommand(VersionUpdater()).apply {
         increment = "major"
      }
      versionBumpCommand.execute(taxiEnvironment)

      var reloaded = TaxiProjectLoader().withConfigFileAt(taxiEnvironment.projectRoot.resolve("taxi.conf")).load()
      reloaded.version.should.equal("2.0.0")

      versionBumpCommand.increment = "minor"
      versionBumpCommand.execute(taxiEnvironment)

      reloaded = TaxiProjectLoader().withConfigFileAt(taxiEnvironment.projectRoot.resolve("taxi.conf")).load()
      reloaded.version.should.equal("1.2.0")

      versionBumpCommand.increment = "patch"
      versionBumpCommand.execute(taxiEnvironment)

      reloaded = TaxiProjectLoader().withConfigFileAt(taxiEnvironment.projectRoot.resolve("taxi.conf")).load()
      reloaded.version.should.equal("1.1.2")
   }

   @Test
   fun versionBumpIllegalArgument() {
      val versionBumpCommand = VersionBumpCommand(VersionUpdater()).apply {
         increment = "undefined"
      }

      assertThrows<IllegalArgumentException> {
         versionBumpCommand.execute(taxiEnvironment)
      }
   }

   @Test
   fun canSetVersion() {
      val newVersion = "1.2.3"
      val setVersionCommand = SetVersionCommand(VersionUpdater()).apply { version = newVersion }
      setVersionCommand.execute(taxiEnvironment)

      val reloaded = TaxiProjectLoader().withConfigFileAt(taxiEnvironment.projectRoot.resolve("taxi.conf")).load()
      reloaded.version.should.equal(newVersion)
   }

   @Test
   fun setVersionIllegalArgument() {
      val versionBumpCommand = VersionBumpCommand(VersionUpdater()).apply {
         increment = "undefined"
      }

      assertThrows<IllegalArgumentException> {
         versionBumpCommand.execute(taxiEnvironment)
      }
   }
}

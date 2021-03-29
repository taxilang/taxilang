package lang.taxi.cli

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import lang.taxi.cli.commands.ShellCommand
import lang.taxi.cli.config.CliTaxiEnvironment
import lang.taxi.cli.config.TaxiProjectLoader
import lang.taxi.cli.utils.log
import lang.taxi.generators.TaxiEnvironment
import lang.taxi.packages.TaxiPackageProject
import org.beryx.textio.TextIO
import org.beryx.textio.TextIoFactory
import org.slf4j.LoggerFactory
import org.springframework.boot.Banner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import java.nio.file.Paths
import java.util.*

@SpringBootApplication()
class TaxiCli {
   companion object {
      lateinit var bootOptions: CliOptions

      @JvmStatic
      fun main(args: Array<String>) {
         bootOptions = parseBootOptions(args)
         if (!bootOptions.taxiFileExists) {
            log().debug("Running outside of a taxi project")
         }
         enableDebugLoggingIfAppropriate(bootOptions)
         val app = SpringApplication(TaxiCli::class.java)
         app.setBannerMode(Banner.Mode.OFF)
         app.run(*args)
      }

      /**
       * Parse options at startup, prior to properly configuring
       * the available commands.  Gives early access to debug
       * flags etc.
       */
      private fun parseBootOptions(args: Array<String>): CliOptions {
         val tempOptions = BootOptions()
         val jCommander = JCommander(tempOptions)
         jCommander.parseWithoutValidation(*args)
         return tempOptions
      }

      /**
       * Called multiple times during startup,
       * as Spring boot overrides the log level when it
       * reads the logback.xml file.
       * @param options
       */
      private fun enableDebugLoggingIfAppropriate(options: CliOptions) {
         if (options.debug) {
            val taxiLogger = LoggerFactory.getLogger("lang.taxi") as Logger
            taxiLogger.level = Level.DEBUG
            taxiLogger.debug("Debug logging enabled")
         }
      }
   }

   @Bean
   fun textIo():TextIO {
       return TextIoFactory.getTextIO()
   }

   @Bean
   fun cliOptions(): CliOptions {
      return CliOptions()
   }

   @Bean
   internal fun jCommander(shellCommands: List<ShellCommand<*>>): JCommander {
      val jCommander = JCommander(bootOptions)
      jCommander.programName = "taxi"
      shellCommands.forEach { command -> jCommander.addCommand(command.name, command) }
      return jCommander
   }

   @Bean
   fun env(): TaxiPackageProject? {
      // Ensure that Spring loading the logback.xml hasn't overridden debug logging
      enableDebugLoggingIfAppropriate(bootOptions)
      return if (bootOptions.taxiFileExists) {
         TaxiProjectLoader()
            .withConfigFileAt(bootOptions.getTaxiFile().toPath())
            .load()
      } else {
         null
      }
   }

   @Bean
   fun environment(project: TaxiPackageProject?): TaxiEnvironment {
      return CliTaxiEnvironment.forRoot(Paths.get(TaxiCli.bootOptions.projectHome), project)
   }

}

// Extends CliOptions and makes it less strict,
// to allow parsing before commands have been wired in
internal open class BootOptions : CliOptions() {
   @Parameter(description = "Main options")
   var leftOverArgs = ArrayList<String>()

}

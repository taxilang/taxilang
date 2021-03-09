package lang.taxi.cli.commands

import com.beust.jcommander.Parameters
import lang.taxi.CompilationError
import lang.taxi.Compiler
import lang.taxi.TaxiDocument
import lang.taxi.cli.plugins.PluginRegistry
import lang.taxi.cli.utils.log
import lang.taxi.generators.ModelGenerator
import lang.taxi.generators.Processor
import lang.taxi.generators.TaxiProjectEnvironment
import lang.taxi.generators.WritableSource
import lang.taxi.packages.TaxiSourcesLoader
import lang.taxi.plugins.Plugin
import org.apache.commons.io.FileUtils
import org.springframework.stereotype.Component
import java.nio.charset.Charset
import java.nio.file.Path
import kotlin.system.exitProcess


@Component
@Parameters(commandDescription = "Builds the current taxi project")
class BuildCommand(private val pluginManager: PluginRegistry) : ProjectShellCommand {
   override val name = "build"

   override fun execute(environment: TaxiProjectEnvironment) {
      val (messages, doc) = loadSources(environment)
      messages.forEach { message ->
         when (message.severity) {
            CompilationError.Severity.INFO -> log().info(message.toString())
            CompilationError.Severity.WARNING -> log().warn(message.toString())
            CompilationError.Severity.ERROR -> log().error(message.toString())
         }
      }
      val processorsFromPlugins = collectProcessorsFromPlugins()

      val sourcesToOutput = pluginManager.declaredPlugins
         .filterIsInstance<ModelGenerator>()
         .flatMap { modelGenerator ->
            val plugin = modelGenerator as Plugin
            log().info("Running generator ${plugin.id}")
            val generated = modelGenerator.generate(doc, processorsFromPlugins, environment)
            log().info("Generator ${plugin.id} generated ${generated.size} files")
            generated
         }

      if (messages.any { it.severity == CompilationError.Severity.ERROR }) {
         log().error("Failed to compile the taxi project")
         exitProcess(1)
      } else if (messages.any { it.severity == CompilationError.Severity.WARNING }) {
         log().warn("Compilation succeeded with warnings")
      } else {
         log().info("Compilation succeeded")
      }
      if (sourcesToOutput.isEmpty()) {
         log().info("No sources were generated. Consider adding a source generator in the taxi.conf ")
      } else {
         writeSources(sourcesToOutput, environment.outputPath)
         log().info("Wrote ${sourcesToOutput.size} files to ${environment.outputPath}")
      }
   }

   private fun collectProcessorsFromPlugins(): List<Processor> {
      val injectors = pluginManager.getComponents<Processor>()
      return injectors
   }

   private fun writeSources(sourcesToOutput: List<WritableSource>, outputPath: Path) {
      sourcesToOutput.forEach { source ->
         val target = outputPath.resolve(source.path)
         FileUtils.forceMkdirParent(target.toFile())
         FileUtils.write(target.toFile(), source.content, Charset.defaultCharset())
      }
   }

   private fun loadSources(projectEnvironment: TaxiProjectEnvironment): Pair<List<CompilationError>, TaxiDocument> {
      val taxiProject = TaxiSourcesLoader.loadPackageAndDependencies(
         projectEnvironment.projectRoot,
         projectEnvironment.project
      )
      return Compiler(taxiProject).compileWithMessages()
   }


}

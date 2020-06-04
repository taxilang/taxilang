package lang.taxi.cli.commands

import lang.taxi.Compiler
import lang.taxi.TaxiDocument
import lang.taxi.cli.plugins.PluginRegistry
import lang.taxi.cli.utils.log
import lang.taxi.generators.ModelGenerator
import lang.taxi.generators.Processor
import lang.taxi.generators.TaxiEnvironment
import lang.taxi.generators.WritableSource
import lang.taxi.packages.TaxiPackageProject
import lang.taxi.packages.TaxiSourcesLoader
import lang.taxi.plugins.Plugin
import org.apache.commons.io.FileUtils
import org.springframework.stereotype.Component
import java.nio.charset.Charset
import java.nio.file.Path


@Component
class BuildCommand(private val pluginManager: PluginRegistry) : ShellCommand {
   override val name = "build"

   override fun execute(environment: TaxiEnvironment) {
      val doc = loadSources(environment.projectRoot) ?: return;
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

      writeSources(sourcesToOutput, environment.outputPath)
      log().info("Wrote ${sourcesToOutput.size} files to ${environment.outputPath}")
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

   private fun loadSources(path: Path): TaxiDocument? {
      val taxiProject = TaxiSourcesLoader.loadPackage(path)

      return Compiler(taxiProject).compile()
   }


}

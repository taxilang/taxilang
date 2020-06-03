package lang.taxi.cli.commands

import com.sun.org.apache.xalan.internal.xsltc.cmdline.Compile
import lang.taxi.Compiler
import lang.taxi.TaxiDocument
import lang.taxi.cli.config.TaxiConfig
import lang.taxi.cli.config.TaxiEnvironment
import lang.taxi.cli.plugins.PluginRegistry
import lang.taxi.cli.utils.log
import lang.taxi.generators.ModelGenerator
import lang.taxi.generators.Processor
import lang.taxi.generators.WritableSource
import lang.taxi.packages.TaxiSourcesLoader
import lang.taxi.plugins.Plugin
import org.antlr.v4.runtime.CharStreams
import org.apache.commons.io.FileUtils
import org.apache.maven.model.Dependency
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Writer
import org.springframework.stereotype.Component
import java.io.File
import java.io.FileWriter
import java.io.Writer
import java.nio.charset.Charset
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*


@Component
class BuildCommand(val config: TaxiConfig, val pluginManager: PluginRegistry) : ShellCommand {
    override val name = "build"

    override fun execute(environment: TaxiEnvironment) {
        val doc = loadSources(environment.sourcePath) ?: return;
        val processorsFromPlugins = collectProcessorsFromPlugins()

        val sourcesToOutput = pluginManager.declaredPlugins
                .filterIsInstance<ModelGenerator>()
                .flatMap { modelGenerator ->
                    val plugin = modelGenerator as Plugin
                    log().info("Running generator ${plugin.id}")
                    val generated = modelGenerator.generate(doc, processorsFromPlugins)
                    log().info("Generator ${plugin.id} generated ${generated.size} files")
                    generated
                }

        writeSources(sourcesToOutput, environment.outputPath)
       generatePom(environment.outputPath)
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

   private fun generatePom(path: Path) {
      val model = Model()
      val writer: Writer = FileWriter("${path}/pom.xml")
      val dependencyList: MutableList<Dependency> = ArrayList<Dependency>()

      // TODO tidy up
      model.modelVersion = "4.0.0"
      model.groupId = "lang.taxi"
      model.artifactId = "parent"
      model.version = "0.5.0-SNAPSHOT"

      val dep = Dependency()
      dep.groupId = "lang.taxi"
      dep.artifactId = "taxi-annotations"
      dep.version = "0.5.0-SNAPSHOT"
      dependencyList.add(dep)

      model.dependencies = dependencyList
      MavenXpp3Writer().write(writer, model)
      writer.close()
   }

}

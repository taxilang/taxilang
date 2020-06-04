package lang.taxi.cli.commands

import io.github.config4k.extract
import lang.taxi.Compiler
import lang.taxi.TaxiDocument
import lang.taxi.cli.config.TaxiConfig
import lang.taxi.cli.config.TaxiEnvironment
import lang.taxi.cli.plugins.PluginRegistry
import lang.taxi.cli.plugins.internal.MavenGeneratorPluginConfig
import lang.taxi.cli.utils.log
import lang.taxi.generators.ModelGenerator
import lang.taxi.generators.Processor
import lang.taxi.generators.WritableSource
import lang.taxi.packages.TaxiSourcesLoader
import lang.taxi.plugins.Plugin
import org.apache.commons.io.FileUtils
import org.apache.maven.model.Dependency
import org.apache.maven.model.DeploymentRepository
import org.apache.maven.model.DistributionManagement
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Writer
import org.springframework.stereotype.Component
import java.io.FileWriter
import java.io.Writer
import java.nio.charset.Charset
import java.nio.file.Path


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
      val mavenConfig = config.project.plugins["kotlin"]?.extract<MavenGeneratorPluginConfig?>("mavenConfig")

      if (mavenConfig != null) {
         val model = Model()
         val writer: Writer = FileWriter("${path}/pom.xml")

         model.modelVersion = mavenConfig.modelVersion
         model.groupId = mavenConfig.groupId
         model.artifactId = mavenConfig.artifactId
         model.version = mavenConfig.version

         if(mavenConfig.distributionManagement != null) {
            model.distributionManagement = DistributionManagement()
            model.distributionManagement.snapshotRepository = DeploymentRepository()
            model.distributionManagement.snapshotRepository.id = mavenConfig.distributionManagement.id
            model.distributionManagement.snapshotRepository.url = mavenConfig.distributionManagement.url
         }

         mavenConfig.dependencies.forEach {
            val dep = Dependency()
            dep.groupId = it.getString("groupId")
            dep.artifactId = it.getString("artifactId")
            dep.version = it.getString("version")

            model.dependencies.add(dep)
         }

         MavenXpp3Writer().write(writer, model)
         writer.close()
      }
   }

}

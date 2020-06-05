package lang.taxi.cli.plugins.internal

import lang.taxi.TaxiDocument
import lang.taxi.cli.plugins.InternalPlugin
import lang.taxi.generators.*
import lang.taxi.plugins.Artifact
import lang.taxi.plugins.PluginWithConfig
import org.apache.commons.io.output.StringBuilderWriter
import org.apache.maven.model.*
import org.apache.maven.model.Dependency
import org.apache.maven.model.DistributionManagement
import org.apache.maven.model.io.xpp3.MavenXpp3Writer
import org.codehaus.plexus.util.xml.Xpp3Dom


class MavenPomGeneratorPlugin : InternalPlugin, ModelGenerator, PluginWithConfig<MavenGeneratorPluginConfig> {
   override val artifact = Artifact.parse("maven")
   private lateinit var config: MavenGeneratorPluginConfig

   override fun setConfig(config: MavenGeneratorPluginConfig) {
      this.config = config
   }

   override fun generate(taxi: TaxiDocument, processors: List<Processor>, environment: TaxiEnvironment): List<WritableSource> {
      val model = Model()
      val writer: StringBuilderWriter = StringBuilderWriter()

      model.modelVersion = config.modelVersion
      model.groupId = config.groupId
      model.artifactId = config.artifactId
      model.version = environment.project.version

      config.distributionManagement?.let { distributionManagement ->
         model.distributionManagement = DistributionManagement()
         model.distributionManagement.snapshotRepository = DeploymentRepository()
         model.distributionManagement.snapshotRepository.id = distributionManagement.id
         model.distributionManagement.snapshotRepository.url = distributionManagement.url
      }
      val mavenDependencies = config.dependencies.map { dependency ->
         val mavenDependency = Dependency().apply {
            groupId = dependency.groupId
            artifactId = dependency.artifactId
            version = dependency.version
         }
         mavenDependency
      }
      model.dependencies.addAll(mavenDependencies)

      model.build = Build()
      model.build.plugins.add(addExecution(addPlugin("org.jetbrains.kotlin", "kotlin-maven-plugin", "1.3.50")))
      model.build.plugins.add(addPlugin("org.jetbrains.kotlin", "maven-compiler-plugin", "3.3"))


      MavenXpp3Writer().write(writer, model)
      writer.close()
      return listOf(SimpleWriteableSource(environment.outputPath.resolve("pom.xml"), writer.toString()))
   }

   private fun addPlugin(groupId: String, artifactId: String, version: String) : Plugin {
      val plugin = Plugin()
      plugin.groupId = groupId
      plugin.artifactId = artifactId
      plugin.version = version

      return plugin
   }

   private fun addExecution(plugin: Plugin) : Plugin {
      val execution = PluginExecution()
      execution.id = "compile"
      execution.goals.add("compile")

      val pluginCompilerConfiguration = Xpp3Dom("configuration")
      val sourceDirs = Xpp3Dom("sourceDirs")
      val sourceDir = Xpp3Dom("sourceDir")
      sourceDir.value = "kotlin"
      sourceDirs.addChild(sourceDir)
      pluginCompilerConfiguration.addChild(sourceDirs)
      execution.configuration = pluginCompilerConfiguration

      plugin.executions.add(execution)

      return plugin
   }


}

package lang.taxi.cli.plugins.internal

import lang.taxi.TaxiDocument
import lang.taxi.cli.plugins.InternalPlugin
import lang.taxi.generators.*
import lang.taxi.packages.TaxiPackageProject
import lang.taxi.plugins.Artifact
import lang.taxi.plugins.PluginWithConfig
import org.apache.commons.io.output.StringBuilderWriter
import org.apache.maven.model.Dependency
import org.apache.maven.model.DeploymentRepository
import org.apache.maven.model.DistributionManagement
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Writer
import java.io.FileWriter
import java.io.Writer
import java.nio.file.Path

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

      MavenXpp3Writer().write(writer, model)
      writer.close()
      return listOf(SimpleWriteableSource(environment.outputPath.resolve("pom.xml"), writer.toString()))
   }


}

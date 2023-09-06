package lang.taxi.cli.plugins.internal

import lang.taxi.TaxiDocument
import lang.taxi.cli.plugins.InternalPlugin
import lang.taxi.generators.ModelGenerator
import lang.taxi.generators.Processor
import lang.taxi.generators.SimpleWriteableSource
import lang.taxi.generators.TaxiProjectEnvironment
import lang.taxi.generators.WritableSource
import lang.taxi.plugins.Artifact
import lang.taxi.plugins.PluginWithConfig
import org.apache.commons.io.output.StringBuilderWriter
import org.apache.maven.model.Dependency
import org.apache.maven.model.DeploymentRepository
import org.apache.maven.model.DistributionManagement
import org.apache.maven.model.Model
import org.apache.maven.model.RepositoryPolicy
import org.apache.maven.model.io.xpp3.MavenXpp3Writer
import org.apache.maven.model.Repository as MavenRepository


class MavenPomGeneratorPlugin(private val configurers: List<MavenModelConfigurer> = emptyList()) : InternalPlugin, ModelGenerator, PluginWithConfig<MavenGeneratorPluginConfig> {
   companion object {
      val TAXI_REPOSITORIES = listOf(
         Repository(
            id = "taxi-snapshots",
            url = "https://repo.orbitalhq.com/snapshot",
            snapshots = true
         ),
         Repository(
            id = "taxi-releases",
            url = "https://repo.orbitalhq.com/release",
            snapshots = false,
            releases = true
         )
      )
   }

   constructor(vararg configurers: MavenModelConfigurer) : this(configurers.toList())

   override val artifact = Artifact.parse("maven")
   private lateinit var config: MavenGeneratorPluginConfig

   override fun setConfig(config: MavenGeneratorPluginConfig) {
      this.config = config
   }

   override fun generate(taxi: TaxiDocument, processors: List<Processor>, environment: TaxiProjectEnvironment): List<WritableSource> {
      val model = Model()
      val writer = StringBuilderWriter()

      model.modelVersion = config.modelVersion
      model.groupId = config.groupId
      model.artifactId = config.artifactId
      model.version = environment.project.version

      config.distributionManagement?.let { distributionManagement ->
         model.distributionManagement = DistributionManagement()
         model.distributionManagement.repository = DeploymentRepository().apply {
            id = distributionManagement.id
            url = distributionManagement.url
         }
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

      model.repositories = mutableListOf()
      (TAXI_REPOSITORIES + config.repositories).forEach { repository ->
         model.repositories.add(
            MavenRepository().apply {
               id = repository.id
               name = repository.name
               url = repository.url

               if (repository.snapshots) {
                  snapshots = RepositoryPolicy().apply {
                     enabled = "true"
                  }
               }
               if (repository.releases) {
                  releases = RepositoryPolicy().apply {
                     enabled = "true"
                  }
               }
            })
      }

      val configuredModel = configurers.fold(model) { acc, configurer -> configurer(acc) }

      MavenXpp3Writer().write(writer, configuredModel)
      writer.close()
      return listOf(SimpleWriteableSource(environment.outputPath.resolve("pom.xml"), writer.toString()))
   }
}

/**
 * An interface for other plugins etc to provide more advanced
 * configuration into the Maven build lifecycle.
 *
 * This allows configuration options that are more advanced than
 * we want to necessarily expose via taxi.conf
 */
typealias MavenModelConfigurer = (model: Model) -> Model

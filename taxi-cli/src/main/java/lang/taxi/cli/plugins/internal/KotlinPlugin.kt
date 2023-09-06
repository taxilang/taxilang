package lang.taxi.cli.plugins.internal

import lang.taxi.TaxiDocument
import lang.taxi.cli.plugins.InternalPlugin
import lang.taxi.generators.ModelGenerator
import lang.taxi.generators.Processor
import lang.taxi.generators.TaxiProjectEnvironment
import lang.taxi.generators.WritableSource
import lang.taxi.generators.kotlin.KotlinGenerator
import lang.taxi.plugins.Artifact
import lang.taxi.plugins.PluginWithConfig
import org.apache.maven.model.Build
import org.apache.maven.model.Model
import org.apache.maven.model.Plugin
import org.apache.maven.model.PluginExecution
import org.springframework.boot.info.BuildProperties
import org.springframework.stereotype.Component
import java.nio.file.Paths
import java.util.*

@Component
class KotlinPlugin(val buildInfo: BuildProperties?) : InternalPlugin, ModelGenerator, PluginWithConfig<KotlinPluginConfig> {
   private lateinit var config: KotlinPluginConfig

   val taxiVersion: String
      get() {
         return config.taxiVersion ?: buildInfo?.version ?: "develop"
      }

   override fun setConfig(config: KotlinPluginConfig) {
      this.config = config
   }


   override fun generate(taxi: TaxiDocument, processors: List<Processor>, environment: TaxiProjectEnvironment): List<WritableSource> {
      val outputPathRoot = if (config.maven == null) Paths.get(config.outputPath) else environment.outputPath.resolve("src/main/java")
      val defaultPackageName =
         config.generatedTypeNamesPackageName ?:
         environment.project.identifier.name.organisation
         .replace("/",".")
         .replace("@","")

      val generator: KotlinGenerator = KotlinGenerator(typeNamesTopLevelPackageName = defaultPackageName)
      val sources = generator.generate(taxi, processors, environment)
         .map { RelativeWriteableSource(outputPathRoot, it) }

      val mavenGenerated = if (config.maven != null) {
         applyMavenConfiguration(taxi, processors, environment)

      } else emptyList()

      return sources + mavenGenerated
   }

   private fun applyMavenConfiguration(taxi: TaxiDocument, processors: List<Processor>, environment: TaxiProjectEnvironment): List<WritableSource> {
      val mavenKotinConfigurer: MavenModelConfigurer = { model: Model ->
         model.dependencies.add(org.apache.maven.model.Dependency().apply {
            groupId = "org.jetbrains.kotlin"
            artifactId = "kotlin-stdlib"
            version = property("kotlin.version")
         })
         model.dependencies.add(org.apache.maven.model.Dependency().apply {
            groupId = "org.taxilang"
            artifactId = "taxi-annotations"
            version = property("taxi.version")
         })


         if (model.properties == null) {
            model.properties = Properties()
         }
         model.properties.set("maven.compiler.source", config.jvmTarget)
         model.properties.set("maven.compiler.target", config.jvmTarget)

         model.properties.set("kotlin.version", config.kotlinVersion)
         model.properties.set("taxi.version", taxiVersion)

         if (model.build == null) {
            model.build = Build()
         }

         model.build.addPlugin(Plugin().apply {
            groupId = "org.jetbrains.kotlin"
            artifactId = "kotlin-maven-plugin"
            version = property("kotlin.version")

            executions.add(PluginExecution().apply {
               id = "compile"
               phase = "compile"
               goals = listOf("compile")
            })
         })

         model
      }
      val mavenGenerator = MavenPomGeneratorPlugin(mavenKotinConfigurer)
      mavenGenerator.setConfig(config.maven!!)

      return mavenGenerator.generate(taxi, processors, environment)
   }

   private fun property(s: String) = "\${$s}"

   override val artifact = Artifact.parse("kotlin")
}

data class KotlinPluginConfig(
   val outputPath: String = "kotlin",
   val kotlinVersion: String = "1.8.10",
   val kotlinLanguageVersion: String = "1.8",
   val jvmTarget: String = "17",
   val maven: MavenGeneratorPluginConfig?,
   val taxiVersion: String? = null,
   // Will default to the organisation name from the project if not defined
   val generatedTypeNamesPackageName: String? = null
)


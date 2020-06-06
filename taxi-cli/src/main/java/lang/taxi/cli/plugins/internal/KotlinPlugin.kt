package lang.taxi.cli.plugins.internal

import lang.taxi.TaxiDocument
import lang.taxi.cli.plugins.InternalPlugin
import lang.taxi.generators.ModelGenerator
import lang.taxi.generators.Processor
import lang.taxi.generators.TaxiEnvironment
import lang.taxi.generators.WritableSource
import lang.taxi.generators.kotlin.KotlinGenerator
import lang.taxi.plugins.Artifact
import lang.taxi.plugins.PluginWithConfig
import org.apache.maven.model.Build
import org.apache.maven.model.Model
import org.apache.maven.model.Plugin
import org.apache.maven.model.PluginExecution
import org.springframework.stereotype.Component
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

@Component
class KotlinPlugin : InternalPlugin, ModelGenerator, PluginWithConfig<KotlinPluginConfig> {
   private lateinit var config: KotlinPluginConfig
   override fun setConfig(config: KotlinPluginConfig) {
      this.config = config
   }

   val generator: KotlinGenerator = KotlinGenerator()

   override fun generate(taxi: TaxiDocument, processors: List<Processor>, environment: TaxiEnvironment): List<WritableSource> {
      val outputPathRoot = if (config.maven == null) Paths.get(config.outputPath) else environment.outputPath.resolve("src/main/java")

      val sources = generator.generate(taxi, processors, environment)
         .map { RelativeWriteableSource(outputPathRoot, it) }

      val mavenGenerated = if (config.maven != null) {
         applyMavenConfiguration(taxi, processors, environment)

      } else emptyList()

      return sources + mavenGenerated
   }

   private fun applyMavenConfiguration(taxi: TaxiDocument, processors: List<Processor>, environment: TaxiEnvironment): List<WritableSource> {
      val mavenKotinConfigurer: MavenModelConfigurer = { model: Model ->
         model.dependencies.add(org.apache.maven.model.Dependency().apply {
            groupId = "org.jetbrains.kotlin"
            artifactId = "kotlin-stdlib"
            version = config.kotlinVersion
         })

         if (model.properties == null) {
            model.properties = Properties()
         }
         model.properties.set("kotlin.compiler.jvmTarget", config.jvmTarget)
         model.properties.set("kotlin.compiler.languageVersion", config.kotlinLanguageVersion)

         if (model.build == null) {
            model.build = Build()
         }

         model.build.addPlugin(Plugin().apply {
            groupId = "org.jetbrains.kotlin"
            artifactId = "kotlin-maven-plugin"
            version = config.kotlinVersion

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

   override val artifact = Artifact.parse("kotlin")
}

data class KotlinPluginConfig(
   val outputPath: String = "kotlin",
   val kotlinVersion: String = "1.3.50",
   val kotlinLanguageVersion: String = "1.3",
   val jvmTarget: String = "1.8",
   val maven: MavenGeneratorPluginConfig?
)

data class RelativeWriteableSource(val relativePath: Path, val source: WritableSource) : WritableSource by source {
   override val path: Path
      get() = relativePath.resolve(source.path)
}

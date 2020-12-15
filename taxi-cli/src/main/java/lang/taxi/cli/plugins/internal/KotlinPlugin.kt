package lang.taxi.cli.plugins.internal

import lang.taxi.TaxiDocument
import lang.taxi.cli.plugins.InternalPlugin
import lang.taxi.generators.ModelGenerator
import lang.taxi.generators.Processor
import lang.taxi.generators.TaxiEnvironment
import lang.taxi.generators.TaxiProjectEnvironment
import lang.taxi.generators.WritableSource
import lang.taxi.generators.kotlin.KotlinGenerator
import lang.taxi.plugins.Artifact
import lang.taxi.plugins.PluginWithConfig
import org.apache.maven.model.Build
import org.apache.maven.model.Model
import org.apache.maven.model.Plugin
import org.apache.maven.model.PluginExecution
import org.codehaus.plexus.util.xml.Xpp3Dom
import org.codehaus.plexus.util.xml.Xpp3DomBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.info.BuildProperties
import org.springframework.stereotype.Component
import java.io.StringReader
import java.nio.file.Path
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
            version = config.kotlinVersion
         })
         model.dependencies.add(org.apache.maven.model.Dependency().apply {
            groupId = "lang.taxi"
            artifactId = "taxi-annotation-processor"
            version = taxiVersion
         })
         model.dependencies.add(org.apache.maven.model.Dependency().apply {
            groupId = "com.google.guava"
            artifactId = "guava"
            version = config.guavaVersion
         })

         if (model.properties == null) {
            model.properties = Properties()
         }
         model.properties.set("maven.compiler.source", config.jvmTarget)
         model.properties.set("maven.compiler.target", config.jvmTarget)
         model.properties.set("kotlin.compiler.jvmTarget", config.jvmTarget)
         model.properties.set("kotlin.compiler.languageVersion", config.kotlinLanguageVersion)

         if (model.build == null) {
            model.build = Build()
         }

         model.build.addPlugin(Plugin().apply {
            groupId = "org.jetbrains.kotlin"
            artifactId = "kotlin-maven-plugin"
            version = config.kotlinVersion

            val pluginConfig = Xpp3DomBuilder.build(
               StringReader(
                  "<configuration>" +
                     "   <sourceDirs>" +
                     "      <sourceDir>src/main/java</sourceDir>" +
                     "   </sourceDirs>" +
                     "   <annotationProcessorPaths>" +
                     "      <annotationProcessorPath>" +
                     "         <groupId>lang.taxi</groupId>" +
                     "         <artifactId>taxi-annotation-processor</artifactId>" +
                     "         <version>${taxiVersion}</version>" +
                     "      </annotationProcessorPath>" +
                     "   </annotationProcessorPaths>" +
                     "</configuration>"))

            executions.add(PluginExecution().apply {
               id = "kapt"
               goals.add("kapt")
               configuration = pluginConfig
            })

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
   val maven: MavenGeneratorPluginConfig?,
   val guavaVersion: String = "28.2-jre",
   val taxiVersion: String? = null,
   // Will default to the organisation name from the project if not defined
   val generatedTypeNamesPackageName: String? = null
)

data class RelativeWriteableSource(val relativePath: Path, val source: WritableSource) : WritableSource by source {
   override val path: Path
      get() = relativePath.resolve(source.path)
}

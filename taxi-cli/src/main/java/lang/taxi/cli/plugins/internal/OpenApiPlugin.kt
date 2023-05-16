package lang.taxi.cli.plugins.internal

import lang.taxi.TaxiDocument
import lang.taxi.cli.plugins.InternalPlugin
import lang.taxi.generators.ModelGenerator
import lang.taxi.generators.Processor
import lang.taxi.generators.TaxiProjectEnvironment
import lang.taxi.generators.WritableSource
import lang.taxi.plugins.Artifact
import lang.taxi.plugins.PluginWithConfig
import org.springframework.stereotype.Component
import org.taxilang.openapi.OpenApiGenerator

data class OpenApiPluginConfig(
   /**
    * A list of services or namespaces to generate.
    * If left empty, then all services containing @HttpOperation operations will be generated
    */
   val services: List<String> = emptyList(),

   val outputPath: String = "open-api",
)

@Component
class OpenApiGeneratorPlugin : PluginWithConfig<OpenApiPluginConfig>, ModelGenerator, InternalPlugin {
   private lateinit var config: OpenApiPluginConfig

   override fun setConfig(config: OpenApiPluginConfig) {
      this.config = config
   }

   override val artifact = Artifact.parse("open-api")

   override fun generate(
      taxi: TaxiDocument,
      processors: List<Processor>,
      environment: TaxiProjectEnvironment
   ): List<WritableSource> {

      val outputPathRoot = environment.outputPath.resolve(config.outputPath)
      val generator = OpenApiGenerator()
      val sources = generator.generateOpenApiSpecAsYaml(taxi, config.services)
         .map { RelativeWriteableSource(outputPathRoot, it) }
      return sources
   }
}


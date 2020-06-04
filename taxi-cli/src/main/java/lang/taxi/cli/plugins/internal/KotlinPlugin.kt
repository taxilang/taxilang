package lang.taxi.cli.plugins.internal

import lang.taxi.TaxiDocument
import lang.taxi.cli.plugins.InternalPlugin
import lang.taxi.plugins.PluginWithConfig
import lang.taxi.generators.ModelGenerator
import lang.taxi.generators.Processor
import lang.taxi.generators.TaxiEnvironment
import lang.taxi.generators.WritableSource
import lang.taxi.generators.kotlin.KotlinGenerator
import lang.taxi.plugins.Artifact
import org.springframework.stereotype.Component
import java.nio.file.Path
import java.nio.file.Paths

@Component
class KotlinPlugin : InternalPlugin, ModelGenerator, PluginWithConfig<KotlinPluginConfig> {
   private lateinit var config: KotlinPluginConfig
   override fun setConfig(config: KotlinPluginConfig) {
      this.config = config
   }

   val generator: KotlinGenerator = KotlinGenerator()

//    override val processors: List<Processor> = generator.processors

   override fun generate(taxi: TaxiDocument, processors: List<Processor>, environment: TaxiEnvironment): List<WritableSource> {
      val sources = generator.generate(taxi, processors, environment)
         .map { RelativeWriteableSource(Paths.get(config.outputPath), it) }

      val mavenGenerated = if (config.maven != null) {
         MavenPomGeneratorPlugin().generate(taxi, processors, environment)
      } else emptyList()

      return sources + mavenGenerated
   }

   override val artifact = Artifact.parse("kotlin")
}

data class KotlinPluginConfig(val outputPath: String = "kotlin", val maven: MavenGeneratorPluginConfig?)

data class RelativeWriteableSource(val relativePath: Path, val source: WritableSource) : WritableSource by source {
   override val path: Path
      get() = relativePath.resolve(source.path)
}

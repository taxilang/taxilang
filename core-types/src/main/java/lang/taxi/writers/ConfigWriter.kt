package lang.taxi.writers

import com.typesafe.config.Config
import com.typesafe.config.ConfigRenderOptions
import io.github.config4k.toConfig
import lang.taxi.packages.TaxiPackageProject

class ConfigWriter {
   fun serialize(project: TaxiPackageProject): String {

      val config = project.toConfig("root")
         .getConfig("root")
         .root()
         .toConfig()
      val root = config.root()
      val configString = root.render(
         ConfigRenderOptions.defaults()
            .setFormatted(true)
            .setOriginComments(false)
            .setJson(false)
      )
      return configString
   }

   fun writeMinimal(project: TaxiPackageProject): String {

      // Can't find a decent way of serializing Hocon in a pretty format.
      // Using this approach until it hurts
      val conf = listOf(
         TaxiPackageProject::name,
         TaxiPackageProject::version,
         TaxiPackageProject::sourceRoot,
         TaxiPackageProject::additionalSources,
         TaxiPackageProject::dependencies
      ).map {
         it.name to it.get(project)
      }.joinToString("\n") { (key, value) -> "$key: $value" }
      return conf + "\n"

   }

   private fun render(config: Config): String {
      return config.root().render(
         ConfigRenderOptions.defaults()
            .setOriginComments(false)
            .setJson(false)
      )
   }
}

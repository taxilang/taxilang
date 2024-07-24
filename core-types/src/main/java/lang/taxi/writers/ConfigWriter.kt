package lang.taxi.writers

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValueFactory
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

   /**
    * Sets a property on the Hocon source to the provided value.
    *
    * Turns out - there's lots of ways to do this wrong.
    * We can't just use a TaxiPackageProject instance, as all the
    * default values get serialized, which is not what the user expects.
    *
    * So, after trying a few different approaches, we're settling on just adding a raw
    * value into Config instance.
    *
    * Ideally, this would append at the end. But, we can't control that.
    */
   fun replaceOrAppendProperty(propertyName: String, value: Any, hoconSource: String): String {
      val wrapped = mapOf(propertyName to value)

      val valueAsConfig = ConfigFactory.empty()
         .withValue(propertyName, ConfigValueFactory.fromAnyRef(value))

      val existing = ConfigFactory.parseString(hoconSource)
      val merged = existing
         .withValue(propertyName, valueAsConfig.getValue(propertyName))

      val configString = merged.root().render(
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

package lang.taxi.packages

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import io.github.config4k.extract
import lang.taxi.packages.utils.log
import org.apache.commons.lang3.SystemUtils
import java.nio.file.Path

// You probably want to use TaxiSourcesLoader,
// which will return the project, and the sources.
class TaxiProjectLoader {

   companion object {
      private val logger = mu.KotlinLogging.logger {}
   }
   private val pathsToSearch = mutableListOf(
      SystemUtils.getUserHome().toPath().resolve(".taxi/taxi.conf")
   )


   /**
    * Specify the actual taxi.conf file (not the containing directory)
    */
   fun withConfigFileAt(path: Path): TaxiProjectLoader {
      pathsToSearch.add(path)
      return this
   }

   fun load(): TaxiPackageProject {

      log().debug("Searching for config files at ${pathsToSearch.joinToString(" , ")}")

      val configs: MutableList<Config> = pathsToSearch.filter { path -> path.toFile().exists() }
         .map { path ->
            log().debug("Reading config at {}", path.toString())
            val config: Config = ConfigFactory.parseFile(path.toFile())
            config
         }.toMutableList()
      configs.add(ConfigFactory.load())
      val config = configs.reduceRight(Config::withFallback)
      log().debug("Effective config:")
      log().debug(config.root().render(ConfigRenderOptions.defaults()))
      return config.extract()
   }
}

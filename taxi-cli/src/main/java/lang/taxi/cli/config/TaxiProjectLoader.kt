package lang.taxi.cli.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import io.github.config4k.extract
import lang.taxi.packages.TaxiPackageProject
import mu.KotlinLogging
import org.apache.commons.lang3.SystemUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path

// Use a TaxiSourcesLoader instead.
class TaxiProjectLoader(searchPaths: List<Path> = DEFAULT_PATHS) {
   private val pathsToSearch: MutableList<Path> = mutableListOf()

   init {
      pathsToSearch.addAll(searchPaths)
   }

   companion object {
      fun noDefaults(): TaxiProjectLoader = TaxiProjectLoader(emptyList())
      val DEFAULT_PATHS = listOf(
         SystemUtils.getUserHome().toPath().resolve(".taxi/taxi.conf")
      )
      private val logger = KotlinLogging.logger {}
   }

   fun withConfigFileAt(path: Path): TaxiProjectLoader {
      pathsToSearch.add(path)
      return this
   }

   fun load(): TaxiPackageProject {

      logger.debug { "Searching for config files at ${pathsToSearch.joinToString(" , ")}" }

      val configs: MutableList<Config> = pathsToSearch.filter { path -> path.toFile().exists() }
         .map { path ->
            logger.debug { "Reading config at $path" }
            val config: Config = ConfigFactory.parseFile(path.toFile())
            config
         }.toMutableList()
      configs.add(ConfigFactory.load())
      val config = configs.reduceRight(Config::withFallback)
      logger.debug("Effective config:")
      logger.debug { config.root().render(ConfigRenderOptions.defaults()) }
      return config.extract()
   }
}

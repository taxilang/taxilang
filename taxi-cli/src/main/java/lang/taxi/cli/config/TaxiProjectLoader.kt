package lang.taxi.cli.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import io.github.config4k.extract
import lang.taxi.packages.TaxiPackageProject
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
      fun noDefaults():TaxiProjectLoader = TaxiProjectLoader(emptyList())
      val DEFAULT_PATHS = listOf(
         SystemUtils.getUserHome().toPath().resolve(".taxi/taxi.conf")
      )
      val log: Logger = LoggerFactory.getLogger(TaxiProjectLoader::class.java)
   }

   fun withConfigFileAt(path: Path): TaxiProjectLoader {
      pathsToSearch.add(path)
      return this
   }

   fun load(): TaxiPackageProject {

      log.debug("Searching for config files at {}", pathsToSearch.joinToString(" , "))

      val configs: MutableList<Config> = pathsToSearch.filter { path -> path.toFile().exists() }
         .map { path ->
            log.debug("Reading config at {}", path.toString())
            val config: Config = ConfigFactory.parseFile(path.toFile())
            config
         }.toMutableList()
      configs.add(ConfigFactory.load())
      val config = configs.reduceRight(Config::withFallback)
      log.debug("Effective config:")
      log.debug(config.root().render(ConfigRenderOptions.defaults()))
      return config.extract()
   }
}

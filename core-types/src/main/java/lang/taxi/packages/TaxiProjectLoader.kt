package lang.taxi.packages

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.github.config4k.extract
import lang.taxi.utils.log
import org.apache.commons.lang3.SystemUtils
import java.nio.file.Path

// You probably want to use TaxiSourcesLoader,
// which will return the project, and the sources.
class TaxiProjectLoader(
   /**
    * Specify the actual taxi.conf file (not the containing directory)
    */
   private val taxiConfPath: Path,
   searchPaths: List<Path> = listOf(SystemUtils.getUserHome().toPath().resolve(".taxi/taxi.conf"))
) {

   private val pathsToSearch = (listOf(taxiConfPath) + searchPaths)
      .toMutableList()

   fun withConfigFileAt(path: Path):TaxiProjectLoader {
      pathsToSearch.add(path)
      return this
   }


   fun load(): TaxiPackageProject {

      log().debug("Searching for config files at ${pathsToSearch.joinToString(" , ")}")

      val configs: MutableList<Config> = pathsToSearch.filter { path -> path.toFile().exists() }
         .map { path ->
            log().debug("Reading config at {}", path)
            val config: Config = ConfigFactory.parseFile(path.toFile())
            config
         }.toMutableList()
      configs.add(ConfigFactory.load())
      val config = configs.reduceRight(Config::withFallback)
      return config.extract<TaxiPackageProject>()
         .copy(taxiConfFile = taxiConfPath)
   }
}

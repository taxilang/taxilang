package lang.taxi.packages

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import io.github.config4k.extract
import lang.taxi.utils.log
import org.apache.commons.lang3.SystemUtils
import java.nio.file.Path

// Note : I've made the path mandatory here, but may
// want to relax that for cli-style projects where there
// is no package yet.
class TaxiPackageLoader(val path: Path? = null) {

   companion object {
      fun forDirectoryContainingTaxiFile(path:Path):TaxiPackageLoader {
         return TaxiPackageLoader(path.resolve("taxi.conf"))
      }
      fun forPathToTaxiFile(path:Path):TaxiPackageLoader {
         return TaxiPackageLoader(path)
      }
   }

   private val pathsToSearch = listOfNotNull(
      path,
      SystemUtils.getUserHome().toPath().resolve(".taxi/taxi.conf")
   ).toMutableList()


   fun withConfigFileAt(path: Path): TaxiPackageLoader {
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
//      log().debug("Effective config:")
//      log().debug(config.root().render(ConfigRenderOptions.defaults()))
      return config.extract()
   }
}

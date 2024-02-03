package lang.taxi.packages

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import io.github.config4k.extract
import lang.taxi.utils.log
import mu.KotlinLogging
import org.apache.commons.lang3.SystemUtils
import java.nio.file.Path

// Note : I've made the path mandatory here, but may
// want to relax that for cli-style projects where there
// is no package yet.
class TaxiPackageLoader(val path: Path? = null) {

   init {
       if (path == null) {
          log().warn("")
       }
   }

   companion object {
      private val logger = KotlinLogging.logger {}
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
            logger.debug{"Reading config at $path" }
            val config = try {
               ConfigFactory.parseFile(path.toFile())
            } catch (e:ConfigException.Parse) {
               throw MalformedTaxiConfFileException(path, e.message ?: e::class.simpleName!!, lineNumber = e.origin()?.lineNumber())
            } catch (e:Exception) {
               throw MalformedTaxiConfFileException(path, e.message ?: e::class.simpleName!!)
            }

            config
         }.toMutableList()
      configs.add(ConfigFactory.load())
      val config = configs.reduceRight(Config::withFallback)
      val loaded:TaxiPackageProject =  config.extract()
      // If we were explicitly given a root path, use that.
      // The "root path" concept is tricky, as the config is actally composed of many locations
      return if (path != null) {
         loaded.copy(packageRootPath = path)
      } else loaded
   }
}


class MalformedTaxiConfFileException(val path: Path, override val message: String, val lineNumber: Int? = null) : RuntimeException(message)

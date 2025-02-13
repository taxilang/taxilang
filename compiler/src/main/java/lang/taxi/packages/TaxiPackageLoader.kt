package lang.taxi.packages

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import io.github.config4k.extract
import lang.taxi.utils.log
import org.apache.commons.lang3.SystemUtils
import java.lang.reflect.InvocationTargetException
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.toPath

// Note : I've made the path mandatory here, but may
// want to relax that for cli-style projects where there
// is no package yet.
class TaxiPackageLoader(val taxiConfFilePath: Path? = null) {

   companion object {
      fun forDirectoryContainingTaxiFile(path: Path): TaxiPackageLoader {
         return TaxiPackageLoader(path.resolve("taxi.conf"))
      }

      fun forPathToTaxiFile(path: Path): TaxiPackageLoader {
         return TaxiPackageLoader(path)
      }

      fun forDirectoryOrFilePath(path: Path): TaxiPackageLoader {
         return if (path.name.endsWith(".conf")) {
            forPathToTaxiFile(path)
         } else {
            forDirectoryContainingTaxiFile(path)
         }
      }
   }

   private val pathsToSearch = listOfNotNull(
      taxiConfFilePath,
      SystemUtils.getUserHome().toPath().resolve(".taxi/taxi.conf")
   ).toMutableList()


   fun withConfigFileAt(path: Path): TaxiPackageLoader {
      pathsToSearch.add(path)
      return this
   }

   fun load(): TaxiPackageProject {

      log().debug("Searching for config files at ${pathsToSearch.joinToString(" , ")}")
      val pathsToLoad = pathsToSearch.filter { it.toFile().exists() }

      val configs: MutableList<Config> = pathsToLoad
         .map { path ->
            log().debug("Reading config at $path")
            val config = try {
               ConfigFactory.parseFile(path.toFile())
            } catch (e: ConfigException) {
               throw MalformedTaxiConfFileException(
                  path,
                  e.message ?: e::class.simpleName!!,
                  lineNumber = e.origin()?.lineNumber()
               )
            } catch (e: Exception) {
               throw MalformedTaxiConfFileException(path, e.message ?: e::class.simpleName!!)
            }

            config
         }.toMutableList()
      configs.add(ConfigFactory.load())
      val config = configs.reduceRight(Config::withFallback)

      val loaded: TaxiPackageProject = try {
         config.extract()
      } catch (e: ConfigException) {
         throw MalformedTaxiConfFileException(
            e.origin().url().toURI().toPath(),
            e.message ?: e::class.simpleName!!,
            lineNumber = e.origin().lineNumber()
         )
      } catch (e: InvocationTargetException) {
         // This is thrown when the TaxiPackageProject couldn't be instantiated -
         // normally a field that is required wasn't provided.
         // This can also be thrown if there was no valid taxi.conf cound
         // If there were multiple files, it's difficult to know which one was the erroring one.
         val pathPart = if (pathsToLoad.size > 1) {
            "The error may have occurred in any of the following files: ${pathsToLoad.joinToString(", ")}"
         } else {
            "File with error: ${pathsToLoad.single()}"
         }
         val message = "An error occurred reading the Taxi project file - ${e.message} - $pathPart"
         throw MalformedTaxiConfFileException(
            pathsToLoad.first(),
            message
         )
      }
      // If we were explicitly given a root path, use that.
      // The "root path" concept is tricky, as the config is actally composed of many locations
      return if (taxiConfFilePath != null) {
         loaded.copy(taxiConfFile = taxiConfFilePath)
      } else loaded
   }
}


class MalformedTaxiConfFileException(val path: Path, override val message: String, val lineNumber: Int? = null) :
   RuntimeException(message)

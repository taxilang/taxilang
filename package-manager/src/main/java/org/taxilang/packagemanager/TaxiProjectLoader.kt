package org.taxilang.packagemanager

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import io.github.config4k.extract
import lang.taxi.packages.TaxiPackageProject
import org.taxilang.packagemanager.utils.log
import org.apache.commons.lang3.SystemUtils
import java.nio.file.Path

// You probably want to use TaxiSourcesLoader,
// which will return the project, and the sources.
class TaxiProjectLoader(
   /**
    * Specify the actual taxi.conf file (not the containing directory)
    */
   private val taxiConfPath: Path
) {

   private val pathsToSearch = mutableListOf(
      taxiConfPath,
      SystemUtils.getUserHome().toPath().resolve(".taxi/taxi.conf")
   )


   fun load(): TaxiPackageProject {

      log().debug("Searching for config files at ${pathsToSearch.joinToString(" , ")}")

      val configs: MutableList<Config> = pathsToSearch.filter { path -> path.toFile().exists() }
         .map { path ->
            log().info("Reading config at {}", path)
            val config: Config = ConfigFactory.parseFile(path.toFile())
            config
         }.toMutableList()
      configs.add(ConfigFactory.load())
      val config = configs.reduceRight(Config::withFallback)
      return config.extract<TaxiPackageProject>()
         .copy(packageRootPath = taxiConfPath.parent)
   }
}

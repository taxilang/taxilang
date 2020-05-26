package lang.taxi.packages

import lang.taxi.sources.SourceCode
import lang.taxi.utils.log
import java.nio.file.Path

class TaxiSourcesLoader(private val sourceRoot: Path) {
   companion object {
      fun forPackageAtPath(packageRootPath: Path): TaxiSourcesLoader {
         val taxiConfFile = packageRootPath.resolve("taxi.conf")
         val taxiPackage = TaxiPackageLoader(taxiConfFile).load()
         return TaxiSourcesLoader(packageRootPath.resolve(taxiPackage.sourceRoot))
      }

      fun loadPackage(packageRootPath: Path): TaxiPackageSources {
         val taxiConfFile = packageRootPath.resolve("taxi.conf")
         val taxiPackage = TaxiPackageLoader(taxiConfFile).load()
         val sourceRoot = packageRootPath.resolve(taxiPackage.sourceRoot)
         val sources = TaxiSourcesLoader(sourceRoot).load()
         return TaxiPackageSources(taxiPackage, sources)
      }
   }

   fun load(): List<SourceCode> {
      val sources = sourceRoot.toFile().walkBottomUp()
         .filter { it.extension == "taxi" }
         .map {
            val pathRelativeToSourceRoot = sourceRoot.relativize(it.toPath()).toString()
            SourceCode(pathRelativeToSourceRoot, it.readText())
         }
         .toList()

      if (sources.isEmpty()) {
         log().warn("No sources were found at $sourceRoot.")
      }

      return sources
   }
}

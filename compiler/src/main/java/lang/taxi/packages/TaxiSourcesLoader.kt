package lang.taxi.packages

import lang.taxi.sources.SourceCode
import lang.taxi.utils.log
import java.nio.file.Path

class TaxiSourcesLoader(private val sourceRoot: Path) {
   companion object {
      fun loadPackage(packageRootPath: Path, project: TaxiPackageProject): TaxiPackageSources {
         val sourceRoot = packageRootPath.resolve(project.sourceRoot)
         val sources = TaxiSourcesLoader(sourceRoot).load()
         return TaxiPackageSources(project, sources)
      }

      fun loadPackage(packageRootPath: Path): TaxiPackageSources {
         val taxiConfFile = packageRootPath.resolve("taxi.conf")
         val taxiPackage = TaxiPackageLoader(taxiConfFile).load()
         return loadPackage(packageRootPath, taxiPackage)
      }
   }

   fun load(): List<SourceCode> {
      val sources = sourceRoot.toFile().walkBottomUp()
         .filter { it.isFile && it.extension == "taxi" }
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

package lang.taxi.packages

import java.nio.file.Path

data class ImporterConfig(val localCache: Path) {
   companion object {
      fun forProject(
         project: TaxiPackageProject
      ): ImporterConfig {
         val localCache = project.taxiHome.resolve("repository/")
         localCache.toFile().mkdirs()
         return ImporterConfig(localCache)
      }
   }
}

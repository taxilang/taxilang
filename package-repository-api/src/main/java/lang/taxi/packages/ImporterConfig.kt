package lang.taxi.packages

import java.nio.file.Path

data class ImporterConfig(val localCache: Path) {
   companion object {
      fun forProject(project: TaxiPackageProject): ImporterConfig {
         return ImporterConfig(project.taxiHome.resolve("repository"))
      }
   }
}

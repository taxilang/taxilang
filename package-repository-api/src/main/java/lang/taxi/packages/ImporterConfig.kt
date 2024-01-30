package lang.taxi.packages

import java.nio.file.Path

data class ImporterConfig(val localCache: Path, val userFacingLogger: MessageLogger = LogWritingMessageLogger) {
   companion object {
      fun forProject(
         project: TaxiPackageProject,
         userFacingLogger: MessageLogger = LogWritingMessageLogger
      ): ImporterConfig {
         val localCache = project.taxiHome.resolve("repository/")
         localCache.toFile().mkdirs()
         return ImporterConfig(localCache, userFacingLogger)
      }
   }
}

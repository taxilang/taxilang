package lang.taxi.packages

import lang.taxi.logging.LogWritingMessageLogger
import lang.taxi.logging.MessageLogger
import java.nio.file.Path

data class ImporterConfig(val localCache: Path,
                          @Deprecated("Just use a normal logger")
                          val userFacingLogger: MessageLogger = LogWritingMessageLogger()) {
   companion object {
      fun forProject(
         project: TaxiPackageProject,
         userFacingLogger: MessageLogger = LogWritingMessageLogger()
      ): ImporterConfig {
         val localCache = project.taxiHome.resolve("repository/")
         localCache.toFile().mkdirs()
         return ImporterConfig(localCache, userFacingLogger)
      }
   }
}

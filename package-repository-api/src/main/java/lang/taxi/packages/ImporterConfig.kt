package lang.taxi.packages

import java.nio.file.Path

data class ImporterConfig(val localCache: Path, val userFacingLogger: MessageLogger = LogWritingMessageLogger) {
   companion object {
      fun forProject(
         project: TaxiPackageProject,
         userFacingLogger: MessageLogger = LogWritingMessageLogger
      ): ImporterConfig {
         return ImporterConfig(project.taxiHome.resolve("repository"), userFacingLogger)
      }
   }
}

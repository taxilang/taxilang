package lang.taxi.lsp.taxiconf

import com.typesafe.config.Config
import io.github.config4k.extract
import lang.taxi.lsp.hocon.HoconParser
import lang.taxi.lsp.hocon.HoconCompletionService
import lang.taxi.packages.TaxiPackageProject
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.Diagnostic

private class TaxiConfParser : HoconParser<TaxiPackageProject>(
   TaxiConfValidator()
) {
   override fun extract(config: Config): TaxiPackageProject = config.extract()
}

/**
 * Service for handling taxi.conf files in the language server.
 *
 * Provides completions, diagnostics, and validation specifically for taxi.conf files
 * using the generic HOCON framework with taxi-specific customizations.
 */
class TaxiConfService {

   companion object {
      /**
       * Fields in TaxiPackageProject that are computed/derived and should not be
       * suggested in completions or validated in the config file.
       */
      private val IGNORED_FIELDS = setOf(
         "identifier",
         "dependencyPackages",
         "packageRootPath",
         "sourceRootPath",
         "taxiConfFile"
      )
   }

   private val hoconService: HoconCompletionService<TaxiPackageProject> = HoconCompletionService.build<TaxiPackageProject>(
      parser = TaxiConfParser(),
      ignoredFields = IGNORED_FIELDS,
      customizers = listOf(TaxiConfCompletionCustomizer()),
      sourceName = "taxi.conf"
   )

   /**
    * Get completions for a taxi.conf file
    */
   fun getCompletions(uri: String, content: String, params: CompletionParams): CompletionList {
      return hoconService.getCompletions(uri, content, params)
   }

   /**
    * Get diagnostics for a taxi.conf file
    */
   fun getDiagnostics(uri: String, content: String): List<Diagnostic> {
      return hoconService.getDiagnostics(uri, content)
   }

   /**
    * Get the cached parse result for a URI
    */
   fun getCachedParseResult(uri: String): TaxiPackageProject? {
      return hoconService.getCachedParseResult(uri)?.value
   }

   /**
    * Clear the cache for a specific URI
    */
   fun clearCache(uri: String) {
      hoconService.clearCache(uri)
   }

   /**
    * Clear all caches
    */
   fun clearAllCaches() {
      hoconService.clearAllCaches()
   }
}

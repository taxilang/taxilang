package lang.taxi.lsp.taxiconf

import lang.taxi.lsp.hocon.HoconService
import lang.taxi.lsp.hocon.hoconService
import lang.taxi.packages.TaxiPackageProject
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.Diagnostic

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

   private val hoconService: HoconService<TaxiPackageProject> = hoconService<TaxiPackageProject>()
      .ignoreFields(*IGNORED_FIELDS.toTypedArray())
      .withValidator(TaxiConfValidator())
      .withCustomizer(TaxiConfCompletionCustomizer())
      .withSourceName("taxi.conf")
      .build()

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

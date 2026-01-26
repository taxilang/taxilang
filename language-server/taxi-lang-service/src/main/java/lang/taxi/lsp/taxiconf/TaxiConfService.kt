package lang.taxi.lsp.taxiconf

import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.Position
import java.util.concurrent.ConcurrentHashMap

/**
 * Main service for handling taxi.conf files in the language server.
 * Provides completions, diagnostics, and validation.
 */
class TaxiConfService {

   private val schemaProvider = TaxiConfSchemaProvider()
   private val parser = TaxiConfParser()
   private val completionProvider = TaxiConfCompletionProvider(schemaProvider)

   // Cache parse results per document URI
   private val parseCache = ConcurrentHashMap<String, TaxiConfParser.ParseResult>()

   /**
    * Get completions for a taxi.conf file
    */
   fun getCompletions(uri: String, content: String, params: CompletionParams): CompletionList {
      val items = completionProvider.getCompletions(content, params.position)
      return CompletionList(false, items)
   }

   /**
    * Get diagnostics for a taxi.conf file
    */
   fun getDiagnostics(uri: String, content: String): List<Diagnostic> {
      val parseResult = parser.parse(content)
      parseCache[uri] = parseResult
      return parseResult.diagnostics
   }

   /**
    * Clear the cache for a specific URI
    */
   fun clearCache(uri: String) {
      parseCache.remove(uri)
   }

   /**
    * Clear all caches
    */
   fun clearAllCaches() {
      parseCache.clear()
   }

   /**
    * Get the cached parse result for a URI
    */
   fun getCachedParseResult(uri: String): TaxiConfParser.ParseResult? {
      return parseCache[uri]
   }
}

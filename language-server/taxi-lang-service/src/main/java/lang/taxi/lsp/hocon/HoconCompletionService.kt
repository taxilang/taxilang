package lang.taxi.lsp.hocon

import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.Diagnostic
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Generic service for handling HOCON files in the language server.
 *
 * Provides completions, diagnostics, and validation for any HOCON file
 * based on a Kotlin data class schema.
 *
 * @param T The Kotlin type representing the HOCON configuration
 */
class HoconCompletionService<T : Any>(
   klass: KClass<T>,
   ignoredFields: Set<String> = emptySet(),
   customizer: HoconCompletionCustomizer? = null,
   private val parser: HoconParser<T>,
   private val sourceName: String = "hocon"
) {

   companion object {
      inline fun <reified T : Any> build(
         parser: HoconParser<T>,
         ignoredFields: Set<String> = emptySet(),
         customizers: List<HoconCompletionCustomizer> = emptyList(),
         sourceName: String = "hocon",
      ): HoconCompletionService<T> {
         val compositeCustomizer = if (customizers.isNotEmpty()) {
            CompositeHoconCompletionCustomizer(customizers)
         } else {
            null
         }

         return HoconCompletionService(
            klass = T::class,
            ignoredFields = ignoredFields,
            customizer = compositeCustomizer,
            sourceName = sourceName,
            parser = parser
         )
      }
   }

   private val schemaProvider = HoconSchemaProvider(klass, ignoredFields)
   private val completionProvider = HoconCompletionProvider(schemaProvider, customizer)

   // Cache parse results per document URI
   private val parseCache = ConcurrentHashMap<String, HoconParser.ParseResult<T>>()

   /**
    * Get completions for a HOCON file
    */
   fun getCompletions(uri: String, content: String, params: CompletionParams): CompletionList {
      val items = completionProvider.getCompletions(content, params.position)
      return CompletionList(false, items)
   }

   /**
    * Get diagnostics for a HOCON file
    */
   fun getDiagnostics(uri: String, content: String): List<Diagnostic> {
      val parseResult = parser.parse(content, sourceName)
      parseCache[uri] = parseResult
      return parseResult.diagnostics
   }

   /**
    * Get the schema provider for advanced usage
    */
   fun getSchemaProvider(): HoconSchemaProvider<T> = schemaProvider

   /**
    * Get the cached parse result for a URI
    */
   fun getCachedParseResult(uri: String): HoconParser.ParseResult<T>? {
      return parseCache[uri]
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
}

package lang.taxi.lsp.hocon

import org.eclipse.lsp4j.CompletionItem
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
class HoconService<T : Any>(
   klass: KClass<T>,
   ignoredFields: Set<String> = emptySet(),
   validator: HoconValidator<T>? = null,
   customizer: HoconCompletionCustomizer? = null,
   private val sourceName: String = "hocon"
) {

   private val schemaProvider = HoconSchemaProvider(klass, ignoredFields)
   private val parser = HoconParser(klass, validator)
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

/**
 * Builder for creating HoconService instances with a fluent API.
 */
class HoconServiceBuilder<T : Any>(private val klass: KClass<T>) {
   private var ignoredFields: Set<String> = emptySet()
   private var validator: HoconValidator<T>? = null
   private val customizers = mutableListOf<HoconCompletionCustomizer>()
   private var sourceName: String = "hocon"

   /**
    * Specify fields to ignore when extracting schema
    */
   fun ignoreFields(vararg fields: String): HoconServiceBuilder<T> {
      this.ignoredFields = fields.toSet()
      return this
   }

   /**
    * Add a custom validator for domain-specific validation
    */
   fun withValidator(validator: HoconValidator<T>): HoconServiceBuilder<T> {
      this.validator = validator
      return this
   }

   /**
    * Add a completion customizer
    */
   fun withCustomizer(customizer: HoconCompletionCustomizer): HoconServiceBuilder<T> {
      this.customizers.add(customizer)
      return this
   }

   /**
    * Set the source name for diagnostics
    */
   fun withSourceName(name: String): HoconServiceBuilder<T> {
      this.sourceName = name
      return this
   }

   /**
    * Build the HoconService
    */
   fun build(): HoconService<T> {
      val compositeCustomizer = if (customizers.isNotEmpty()) {
         CompositeHoconCompletionCustomizer(customizers)
      } else {
         null
      }

      return HoconService(
         klass = klass,
         ignoredFields = ignoredFields,
         validator = validator,
         customizer = compositeCustomizer,
         sourceName = sourceName
      )
   }
}

/**
 * Create a HoconService builder for the specified type
 */
inline fun <reified T : Any> hoconService(): HoconServiceBuilder<T> {
   return HoconServiceBuilder(T::class)
}

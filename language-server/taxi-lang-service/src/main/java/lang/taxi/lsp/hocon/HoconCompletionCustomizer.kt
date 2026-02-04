package lang.taxi.lsp.hocon

import org.eclipse.lsp4j.CompletionItem

/**
 * Extension point for customizing HOCON completions for specific types or contexts.
 *
 * Implement this interface to provide domain-specific completion suggestions
 * that go beyond what can be inferred from reflection alone.
 *
 * Examples:
 * - Suggest known map keys (e.g., linter rule names, plugin names)
 * - Provide context-specific values (e.g., available repositories)
 * - Add custom snippets or templates
 * - Filter or reorder completions based on context
 */
interface HoconCompletionCustomizer {

   /**
    * Customize completions for a map key.
    *
    * Called when the user is typing a key in a map-like structure.
    *
    * @param mapPropertyPath The property path to the map (e.g., "plugins", "linter")
    * @param prefix The current prefix being typed
    * @param schema The schema for the map property
    * @return Custom completion items, or null to use default behavior
    */
   fun getMapKeyCompletions(
      mapPropertyPath: String,
      prefix: String,
      schema: HoconSchemaProvider.PropertySchema
   ): List<CompletionItem>? = null

   /**
    * Customize completions for a value.
    *
    * Called when the user is typing a value (after a colon).
    *
    * @param propertyPath The full property path (e.g., "plugins.taxi/kotlin.enabled")
    * @param prefix The current prefix being typed
    * @param schema The schema for the property
    * @return Custom completion items, or null to use default behavior
    */
   fun getValueCompletions(
      propertyPath: String,
      prefix: String,
      schema: HoconSchemaProvider.PropertySchema
   ): List<CompletionItem>? = null

   /**
    * Customize completions for object properties.
    *
    * Called when the user is typing a property name inside an object.
    *
    * @param objectType The type of the parent object
    * @param prefix The current prefix being typed
    * @param defaultCompletions The default completions that would be provided
    * @return Custom completion items, or null to use default behavior
    */
   fun getObjectPropertyCompletions(
      objectType: HoconSchemaProvider.PropertyType.Object,
      prefix: String,
      defaultCompletions: List<CompletionItem>
   ): List<CompletionItem>? = null

   /**
    * Customize top-level completions.
    *
    * Called when the user is at the top level of the file.
    *
    * @param prefix The current prefix being typed
    * @param defaultCompletions The default completions that would be provided
    * @return Custom completion items, or null to use default behavior
    */
   fun getTopLevelCompletions(
      prefix: String,
      defaultCompletions: List<CompletionItem>
   ): List<CompletionItem>? = null

   /**
    * Customize the snippet for a property.
    *
    * @param property The property schema
    * @return Custom snippet text, or null to use default snippet
    */
   fun getPropertySnippet(property: HoconSchemaProvider.PropertySchema): String? = null

   /**
    * Customize the documentation for a property.
    *
    * @param property The property schema
    * @return Custom documentation text, or null to use default documentation
    */
   fun getPropertyDocumentation(property: HoconSchemaProvider.PropertySchema): String? = null
}

/**
 * A composite customizer that delegates to multiple customizers in order.
 *
 * The first non-null result from any customizer is used.
 */
class CompositeHoconCompletionCustomizer(
   private val customizers: List<HoconCompletionCustomizer>
) : HoconCompletionCustomizer {

   override fun getMapKeyCompletions(
      mapPropertyPath: String,
      prefix: String,
      schema: HoconSchemaProvider.PropertySchema
   ): List<CompletionItem>? {
      return customizers.firstNotNullOfOrNull {
         it.getMapKeyCompletions(mapPropertyPath, prefix, schema)
      }
   }

   override fun getValueCompletions(
      propertyPath: String,
      prefix: String,
      schema: HoconSchemaProvider.PropertySchema
   ): List<CompletionItem>? {
      return customizers.firstNotNullOfOrNull {
         it.getValueCompletions(propertyPath, prefix, schema)
      }
   }

   override fun getObjectPropertyCompletions(
      objectType: HoconSchemaProvider.PropertyType.Object,
      prefix: String,
      defaultCompletions: List<CompletionItem>
   ): List<CompletionItem>? {
      return customizers.firstNotNullOfOrNull {
         it.getObjectPropertyCompletions(objectType, prefix, defaultCompletions)
      }
   }

   override fun getTopLevelCompletions(
      prefix: String,
      defaultCompletions: List<CompletionItem>
   ): List<CompletionItem>? {
      return customizers.firstNotNullOfOrNull {
         it.getTopLevelCompletions(prefix, defaultCompletions)
      }
   }

   override fun getPropertySnippet(property: HoconSchemaProvider.PropertySchema): String? {
      return customizers.firstNotNullOfOrNull {
         it.getPropertySnippet(property)
      }
   }

   override fun getPropertyDocumentation(property: HoconSchemaProvider.PropertySchema): String? {
      return customizers.firstNotNullOfOrNull {
         it.getPropertyDocumentation(property)
      }
   }
}

package lang.taxi.lsp.taxiconf

import lang.taxi.lsp.hocon.HoconCompletionCustomizer
import lang.taxi.lsp.hocon.HoconSchemaProvider
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.InsertTextFormat

/**
 * Taxi-specific completion customizer for taxi.conf files.
 *
 * Provides domain-specific completions for:
 * - Known linter rule names
 * - Common plugin names
 * - Other taxi-specific conventions
 */
class TaxiConfCompletionCustomizer : HoconCompletionCustomizer {

   companion object {
      // Known linter rules - these could be dynamically loaded from the compiler in the future
      private val KNOWN_LINTER_RULES = listOf(
         "no-duplicate-types-on-models",
         "no-primitive-types-on-models",
         "unused-import",
         "type-naming-convention"
      )

      // Common plugin names
      private val KNOWN_PLUGINS = listOf(
         "taxi/kotlin",
         "taxi/open-api",
         "taxi/swagger",
         "taxi/protobuf"
      )
   }

   override fun getMapKeyCompletions(
      mapPropertyPath: String,
      prefix: String,
      schema: HoconSchemaProvider.PropertySchema
   ): List<CompletionItem>? {
      return when {
         mapPropertyPath == "linter" || mapPropertyPath.endsWith(".linter") -> {
            getLinterRuleCompletions(prefix)
         }
         mapPropertyPath == "plugins" || mapPropertyPath.endsWith(".plugins") -> {
            getPluginNameCompletions(prefix)
         }
         else -> null
      }
   }

   private fun getLinterRuleCompletions(prefix: String): List<CompletionItem> {
      return KNOWN_LINTER_RULES.map { rule ->
         val item = CompletionItem(rule)
         item.kind = CompletionItemKind.Property
         item.insertText = "$rule: {\n  enabled: true\n  severity: \${1|INFO,WARNING,ERROR|}\n}"
         item.insertTextFormat = InsertTextFormat.Snippet
         item.detail = "Linter Rule Configuration"
         item.documentation = "Configure the $rule linter rule"
         item
      }.filter { it.label.startsWith(prefix, ignoreCase = true) }
   }

   private fun getPluginNameCompletions(prefix: String): List<CompletionItem> {
      return KNOWN_PLUGINS.map { plugin ->
         val item = CompletionItem(plugin)
         item.kind = CompletionItemKind.Module
         item.insertText = "\"$plugin\": {\n  \$1\n}"
         item.insertTextFormat = InsertTextFormat.Snippet
         item.detail = "Taxi Plugin"
         item.documentation = "Configure the $plugin plugin"
         item
      }.filter { it.label.startsWith(prefix, ignoreCase = true) }
   }

   override fun getPropertyDocumentation(property: HoconSchemaProvider.PropertySchema): String? {
      // Add taxi-specific documentation for certain properties
      return when (property.name) {
         "name" -> """
            Project identifier in the format: namespace/project-name

            Example: org.taxilang/my-project
         """.trimIndent()
         "version" -> """
            Project version following semantic versioning

            Format: X.Y.Z or X.Y.Z-SNAPSHOT
            Example: 1.0.0, 2.1.3-SNAPSHOT
         """.trimIndent()
         "sourceRoot" -> """
            Directory containing Taxi source files

            Default: .
            Example: src/
         """.trimIndent()
         "output" -> """
            Directory for compiled output

            Default: dist/
         """.trimIndent()
         "plugins" -> """
            Plugin configurations

            Plugins extend Taxi's functionality for code generation and integration.
            Common plugins: taxi/kotlin, taxi/open-api, taxi/swagger
         """.trimIndent()
         "linter" -> """
            Linter rule configurations

            Configure individual linter rules with severity levels (INFO, WARNING, ERROR)
            and enable/disable them.
         """.trimIndent()
         "dependencies" -> """
            Package dependencies

            Map of package names to versions.
            Example:
              dependencies: {
                "org.taxilang/stdlib": "1.0.0"
              }
         """.trimIndent()
         "repositories" -> """
            Package repositories for dependency resolution

            Configure remote repositories where dependencies can be found.
         """.trimIndent()
         else -> null
      }
   }
}

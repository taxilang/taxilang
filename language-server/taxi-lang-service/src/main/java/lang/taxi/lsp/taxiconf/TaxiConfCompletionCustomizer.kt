package lang.taxi.lsp.taxiconf

import lang.taxi.lsp.hocon.HoconCompletionCustomizer
import lang.taxi.lsp.hocon.HoconSchemaProvider
import lang.taxi.packages.GlobPattern
import lang.taxi.packages.SourcesType
import lang.taxi.packages.TaxiPackageProject
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.InsertTextFormat
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind
import org.eclipse.lsp4j.jsonrpc.messages.Either
import kotlin.reflect.KProperty1


private fun completion(
   label: String,
   kind: CompletionItemKind,
   insertText: String,
   documentation: String,
   insertTextFormat: InsertTextFormat = InsertTextFormat.PlainText
): CompletionItem {
   return CompletionItem(label).apply {
      this.kind = kind
      this.insertText = insertText
      this.insertTextFormat = insertTextFormat
      this.documentation = Either.forLeft(documentation)
   }
}

private fun markdown(value: String): MarkupContent = MarkupContent(MarkupKind.MARKDOWN, value)
private fun completion(
   label: String,
   kind: CompletionItemKind,
   insertText: String,
   documentation: MarkupContent,
   insertTextFormat: InsertTextFormat = InsertTextFormat.PlainText
): CompletionItem {
   return CompletionItem(label).apply {
      this.kind = kind
      this.insertText = insertText
      this.insertTextFormat = insertTextFormat
      this.documentation = Either.forRight(documentation)
   }
}

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

      private val KNOWN_ADDITIONAL_SOURCES = listOf(
         completion(
            "@orbital/config",
            CompletionItemKind.Snippet,
            """"@orbital/config" : "config/*.conf"""",
            "Location for orbital config files, like auth.conf, services.conf, env.conf and connections.conf"
         ),
         completion(
            "@orbital/nebula",
            CompletionItemKind.Snippet,
            """"@orbital/nebula" : "nebula/*.nebula.kts"""",
            markdown("Location for Nebula scripting files. See [Stubbing Services](https://orbitalhq.com/docs/testing/stubbing-services) for more detail")
         ),
         completion(
            "@orbital/avro",
            CompletionItemKind.Snippet,
            """"@orbital/avro" : "avro/*.avsc"""",
            markdown("Location for Avro source files. See [Avro docs](https://orbitalhq.com/docs/data-formats/avro) for more detail")
         ),
         completion(
            "@orbital/openapi",
            CompletionItemKind.Snippet,
            """"@orbital/openapi" : "openapi/*.yaml"""",
            markdown("Location for OpenAPI schema files. See [OpenAPI docs](https://orbitalhq.com/docs/describing-data-sources/open-api) for more detail")
         ),
         completion(
            "@orbital/protobuf",
            CompletionItemKind.Snippet,
            """"@orbital/protobuf" : "proto/src/**/*.proto"""",
            markdown("Location for Protobuf spec files. See [Protobuf docs](https://orbitalhq.com/docs/describing-data-sources/protobuf) for more detail")
         ),
         completion(
            "@orbital/xsd",
            CompletionItemKind.Snippet,
            """"@orbital/xsd" : "xsd/*.xsd"""",
            markdown("Location for XSD schema files. See [Xml docs](https://orbitalhq.com/docs/describing-data-sources/soap) for more detail")
         ),
         completion(
            "@orbital/wsdl",
            CompletionItemKind.Snippet,
            """"@orbital/wsdl" : "wsdl/*.{wsdl,xsd}"""",
            markdown("Location for WSDL schema files. See [Xml docs](https://orbitalhq.com/docs/describing-data-sources/soap) for more detail")
         ),
         completion(
            "@orbital/function-jar",
            CompletionItemKind.Snippet,
            """"@orbital/function-jar" : "functions/*.jar"""",
            markdown("Location for custom function JAR files. See [Custom Functions](https://orbitalhq.com/docs/extending/custom-functions) for more detail")
         ),
         completion(
            "@orbital/function-kts",
            CompletionItemKind.Snippet,
            """"@orbital/function-kts" : "functions/*.taxi.kts"""",
            markdown("Location for custom function script files. See [Custom Functions](https://orbitalhq.com/docs/extending/custom-functions) for more detail")
         ),
      )
   }

   private fun matchesKey(
      mapPropertyPath: String,
      property: KProperty1<*, *>
   ): Boolean {
      return mapPropertyPath == property.name || mapPropertyPath.endsWith(".${property.name}")
   }

   override fun getMapKeyCompletions(
      mapPropertyPath: String,
      prefix: String,
      schema: HoconSchemaProvider.PropertySchema
   ): List<CompletionItem>? {
      return when {
         matchesKey(mapPropertyPath, TaxiPackageProject::linter) -> getLinterRuleCompletions(prefix)
         matchesKey(mapPropertyPath, TaxiPackageProject::plugins) -> getPluginNameCompletions(prefix)
         matchesKey(mapPropertyPath, TaxiPackageProject::additionalSources) -> getAdditionalSourcesCompletions(prefix)

         else -> null
      }
   }

   private fun getAdditionalSourcesCompletions(prefix: String): List<CompletionItem> {
      return KNOWN_ADDITIONAL_SOURCES
   }

   private fun getLinterRuleCompletions(prefix: String): List<CompletionItem> {
      return KNOWN_LINTER_RULES.map { rule ->
         val item = CompletionItem(rule)
         item.kind = CompletionItemKind.Property
         item.insertText = "$rule: {\n  enabled: true\n  severity: \${1|INFO,WARNING,ERROR|}\n}"
         item.insertTextFormat = InsertTextFormat.Snippet
         item.detail = "Linter Rule Configuration"
         item.documentation = Either.forLeft("Configure the $rule linter rule")
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
         item.documentation = Either.forLeft("Configure the $plugin plugin")
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

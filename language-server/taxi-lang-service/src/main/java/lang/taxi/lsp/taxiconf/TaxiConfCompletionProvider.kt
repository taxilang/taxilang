package lang.taxi.lsp.taxiconf

import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.InsertTextFormat
import org.eclipse.lsp4j.Position

/**
 * Provides completion suggestions for taxi.conf files based on the schema.
 */
class TaxiConfCompletionProvider(private val schemaProvider: TaxiConfSchemaProvider) {

   /**
    * Get completions for a given position in a taxi.conf file
    */
   fun getCompletions(content: String, position: Position): List<CompletionItem> {
      val lines = content.split("\n")
      val currentLine = if (position.line < lines.size) lines[position.line] else ""
      val currentText = currentLine.substring(0, position.character.coerceAtMost(currentLine.length))

      // Determine the current context (what we're completing)
      val context = analyzeContext(lines, position)

      return when (context) {
         is CompletionContext.TopLevel -> getTopLevelCompletions()
         is CompletionContext.ObjectProperty -> getObjectPropertyCompletions(context.objectType, context.prefix)
         is CompletionContext.MapKey -> getMapKeyCompletions(context.mapName, context.prefix)
         is CompletionContext.Value -> getValueCompletions(context.propertyPath, context.prefix)
         is CompletionContext.Unknown -> emptyList()
      }
   }

   private fun getTopLevelCompletions(): List<CompletionItem> {
      return schemaProvider.getSchema().map { property ->
         val item = CompletionItem(property.name)
         item.kind = when (property.type) {
            is TaxiConfSchemaProvider.PropertyType.Primitive -> CompletionItemKind.Field
            is TaxiConfSchemaProvider.PropertyType.Object -> CompletionItemKind.Class
            is TaxiConfSchemaProvider.PropertyType.Map -> CompletionItemKind.Struct
            is TaxiConfSchemaProvider.PropertyType.List -> CompletionItemKind.Enum
            is TaxiConfSchemaProvider.PropertyType.Enum -> CompletionItemKind.Enum
         }

         // Add snippet for complex types
         item.insertText = when (property.type) {
            is TaxiConfSchemaProvider.PropertyType.Primitive -> {
               if (property.defaultValue != null) {
                  "${property.name}: \"${property.defaultValue}\""
               } else {
                  "${property.name}: \"\$1\""
               }
            }
            is TaxiConfSchemaProvider.PropertyType.Object -> {
               "${property.name}: {\n  \$1\n}"
            }
            is TaxiConfSchemaProvider.PropertyType.Map -> {
               "${property.name}: {\n  \$1\n}"
            }
            is TaxiConfSchemaProvider.PropertyType.List -> {
               "${property.name}: [\n  \$1\n]"
            }
            is TaxiConfSchemaProvider.PropertyType.Enum -> {
               "${property.name}: \$1"
            }
         }
         item.insertTextFormat = InsertTextFormat.Snippet

         // Add documentation
         val typeDesc = getTypeDescription(property.type)
         item.detail = typeDesc
         item.documentation = buildDocumentation(property)

         item
      }
   }

   private fun getObjectPropertyCompletions(objectType: String, prefix: String): List<CompletionItem> {
      val properties = schemaProvider.getObjectProperties(objectType)
      return properties.map { property ->
         val item = CompletionItem(property.name)
         item.kind = CompletionItemKind.Field

         item.insertText = when (property.type) {
            is TaxiConfSchemaProvider.PropertyType.Primitive -> {
               "${property.name}: \"\$1\""
            }
            else -> {
               "${property.name}: \$1"
            }
         }
         item.insertTextFormat = InsertTextFormat.Snippet
         item.detail = getTypeDescription(property.type)
         item.documentation = buildDocumentation(property)

         item
      }.filter { it.label.startsWith(prefix, ignoreCase = true) }
   }

   private fun getMapKeyCompletions(mapName: String, prefix: String): List<CompletionItem> {
      // For certain maps, we can provide known key suggestions
      return when (mapName) {
         "linter" -> getLinterRuleCompletions(prefix)
         "plugins" -> getPluginNameCompletions(prefix)
         else -> emptyList()
      }
   }

   private fun getLinterRuleCompletions(prefix: String): List<CompletionItem> {
      // Common linter rules (these could be extracted from the compiler if needed)
      val knownRules = listOf(
         "no-duplicate-types-on-models",
         "no-primitive-types-on-models",
         "unused-import",
         "type-naming-convention"
      )

      return knownRules.map { rule ->
         val item = CompletionItem(rule)
         item.kind = CompletionItemKind.Property
         item.insertText = "$rule: {\n  enabled: true\n  severity: \${1|INFO,WARNING,ERROR|}\n}"
         item.insertTextFormat = InsertTextFormat.Snippet
         item.detail = "Linter Rule Configuration"
         item
      }.filter { it.label.startsWith(prefix, ignoreCase = true) }
   }

   private fun getPluginNameCompletions(prefix: String): List<CompletionItem> {
      // Common plugin names
      val knownPlugins = listOf(
         "taxi/kotlin",
         "taxi/open-api",
         "taxi/swagger",
         "taxi/protobuf"
      )

      return knownPlugins.map { plugin ->
         val item = CompletionItem(plugin)
         item.kind = CompletionItemKind.Module
         item.insertText = "\"$plugin\": {\n  \$1\n}"
         item.insertTextFormat = InsertTextFormat.Snippet
         item.detail = "Taxi Plugin"
         item
      }.filter { it.label.startsWith(prefix, ignoreCase = true) }
   }

   private fun getValueCompletions(propertyPath: String, prefix: String): List<CompletionItem> {
      val property = schemaProvider.getPropertySchema(propertyPath) ?: return emptyList()

      return when (val type = property.type) {
         is TaxiConfSchemaProvider.PropertyType.Enum -> {
            type.values.map { value ->
               val item = CompletionItem(value)
               item.kind = CompletionItemKind.EnumMember
               item.detail = type.className
               item
            }
         }
         is TaxiConfSchemaProvider.PropertyType.Primitive -> {
            if (type.typeName == "Boolean") {
               listOf("true", "false").map { value ->
                  val item = CompletionItem(value)
                  item.kind = CompletionItemKind.Value
                  item
               }
            } else {
               emptyList()
            }
         }
         else -> emptyList()
      }.filter { it.label.startsWith(prefix, ignoreCase = true) }
   }

   private fun analyzeContext(lines: List<String>, position: Position): CompletionContext {
      if (position.line >= lines.size) {
         return CompletionContext.TopLevel
      }

      val currentLine = lines[position.line]
      val beforeCursor = currentLine.substring(0, position.character.coerceAtMost(currentLine.length))

      // Simple heuristic-based context detection
      // This is simplified - a proper implementation would parse the HOCON structure

      // Check if we're at top level (no indentation or minimal indentation)
      val trimmed = beforeCursor.trim()
      if (trimmed.isEmpty() || (!trimmed.contains(':') && !trimmed.contains('{'))) {
         return CompletionContext.TopLevel
      }

      // Try to determine the property path by looking at previous lines
      val propertyPath = extractPropertyPath(lines, position)

      // Check if we're in a map-like structure
      if (propertyPath.contains("linter") || propertyPath.contains("plugins")) {
         val mapName = propertyPath.substringBefore('.')
         return CompletionContext.MapKey(mapName, trimmed)
      }

      // Check if we're completing a value (after colon)
      if (beforeCursor.contains(':')) {
         val afterColon = beforeCursor.substringAfterLast(':').trim()
         return CompletionContext.Value(propertyPath, afterColon)
      }

      return CompletionContext.Unknown
   }

   private fun extractPropertyPath(lines: List<String>, position: Position): String {
      // Walk backwards through lines to build the property path
      val path = mutableListOf<String>()
      var currentIndent = getIndentLevel(lines.getOrNull(position.line) ?: "")

      for (i in position.line downTo 0) {
         val line = lines[i].trim()
         if (line.isEmpty() || line.startsWith("//") || line.startsWith("#")) continue

         val indent = getIndentLevel(lines[i])
         if (indent < currentIndent) {
            // Found a parent property
            val propertyName = line.substringBefore(':').trim()
            if (propertyName.isNotEmpty()) {
               path.add(0, propertyName)
               currentIndent = indent
            }
         }
      }

      return path.joinToString(".")
   }

   private fun getIndentLevel(line: String): Int {
      return line.takeWhile { it.isWhitespace() }.length
   }

   private fun getTypeDescription(type: TaxiConfSchemaProvider.PropertyType): String {
      return when (type) {
         is TaxiConfSchemaProvider.PropertyType.Primitive -> type.typeName
         is TaxiConfSchemaProvider.PropertyType.Object -> type.className
         is TaxiConfSchemaProvider.PropertyType.Map -> "Map<${type.keyType}, ${getTypeDescription(type.valueType)}>"
         is TaxiConfSchemaProvider.PropertyType.List -> "List<${getTypeDescription(type.elementType)}>"
         is TaxiConfSchemaProvider.PropertyType.Enum -> type.className
      }
   }

   private fun buildDocumentation(property: TaxiConfSchemaProvider.PropertySchema): String {
      val parts = mutableListOf<String>()
      parts.add("Type: ${getTypeDescription(property.type)}")

      if (property.isNullable) {
         parts.add("(optional)")
      }

      if (property.defaultValue != null) {
         parts.add("Default: ${property.defaultValue}")
      }

      return parts.joinToString("\n")
   }

   sealed class CompletionContext {
      object TopLevel : CompletionContext()
      data class ObjectProperty(val objectType: String, val prefix: String) : CompletionContext()
      data class MapKey(val mapName: String, val prefix: String) : CompletionContext()
      data class Value(val propertyPath: String, val prefix: String) : CompletionContext()
      object Unknown : CompletionContext()
   }
}

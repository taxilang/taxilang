package lang.taxi.lsp.hocon

import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.InsertTextFormat
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.jsonrpc.messages.Either

/**
 * Generic completion provider for HOCON files based on Kotlin type reflection.
 *
 * Provides intelligent completions by analyzing:
 * - The schema extracted from the Kotlin type
 * - The current context in the HOCON file
 * - Custom completions from registered customizers
 *
 * @param T The Kotlin type representing the HOCON configuration
 */
class HoconCompletionProvider<T : Any>(
   private val schemaProvider: HoconSchemaProvider<T>,
   private val customizer: HoconCompletionCustomizer? = null
) {

   /**
    * Get completions for a given position in a HOCON file
    */
   fun getCompletions(content: String, position: Position): List<CompletionItem> {
      val lines = content.split("\n")
      val currentLine = if (position.line < lines.size) lines[position.line] else ""
      val currentText = currentLine.substring(0, position.character.coerceAtMost(currentLine.length))

      // Determine the current context (what we're completing)
      val context = analyzeContext(lines, position)

      return when (context) {
         is CompletionContext.TopLevel -> {
            val default = getTopLevelCompletions()
            customizer?.getTopLevelCompletions(currentText.trim(), default) ?: default
         }

         is CompletionContext.ObjectProperty -> {
            val default = getObjectPropertyCompletions(context.objectType, context.prefix)
            customizer?.getObjectPropertyCompletions(context.objectType, context.prefix, default) ?: default
         }

         is CompletionContext.MapKey -> {
            val schema = schemaProvider.getPropertySchema(context.mapName)
            if (schema != null) {
               customizer?.getMapKeyCompletions(context.mapName, context.prefix, schema)
                  ?: emptyList()
            } else {
               emptyList()
            }
         }

         is CompletionContext.Value -> {
            val schema = schemaProvider.getPropertySchema(context.propertyPath)
            if (schema != null) {
               customizer?.getValueCompletions(context.propertyPath, context.prefix, schema)
                  ?: getValueCompletions(schema, context.prefix)
            } else {
               emptyList()
            }
         }

         is CompletionContext.Unknown -> emptyList()
      }
   }

   private fun getTopLevelCompletions(): List<CompletionItem> {
      return schemaProvider.getSchema().map { property ->
         val item = CompletionItem(property.name)
         item.kind = when (property.type) {
            is HoconSchemaProvider.PropertyType.Primitive -> CompletionItemKind.Field
            is HoconSchemaProvider.PropertyType.Object -> CompletionItemKind.Class
            is HoconSchemaProvider.PropertyType.Map -> CompletionItemKind.Struct
            is HoconSchemaProvider.PropertyType.ListType -> CompletionItemKind.Enum
            is HoconSchemaProvider.PropertyType.Enum -> CompletionItemKind.Enum
         }

         // Check for custom snippet first
         val customSnippet = customizer?.getPropertySnippet(property)
         item.insertText = customSnippet ?: getDefaultSnippet(property)
         item.insertTextFormat = InsertTextFormat.Snippet

         // Set details and documentation
         item.detail = schemaProvider.getTypeDescription(property.type)
         item.documentation = Either.forLeft(
            customizer?.getPropertyDocumentation(property)
               ?: buildDocumentation(property)
         )

         item
      }
   }

   private fun getObjectPropertyCompletions(
      objectType: HoconSchemaProvider.PropertyType.Object,
      prefix: String
   ): List<CompletionItem> {
      val properties = schemaProvider.getObjectProperties(objectType)
      return properties.map { property ->
         val item = CompletionItem(property.name)
         item.kind = CompletionItemKind.Field

         val customSnippet = customizer?.getPropertySnippet(property)
         item.insertText = customSnippet ?: when (property.type) {
            is HoconSchemaProvider.PropertyType.Primitive -> {
               "${property.name}: \"\$1\""
            }

            else -> {
               "${property.name}: \$1"
            }
         }
         item.insertTextFormat = InsertTextFormat.Snippet
         item.detail = schemaProvider.getTypeDescription(property.type)
         item.documentation = Either.forLeft(
            customizer?.getPropertyDocumentation(property)
               ?: buildDocumentation(property)
         )

         item
      }.filter { it.label.startsWith(prefix, ignoreCase = true) }
   }

   private fun getValueCompletions(
      schema: HoconSchemaProvider.PropertySchema,
      prefix: String
   ): List<CompletionItem> {
      return when (val type = schema.type) {
         is HoconSchemaProvider.PropertyType.Enum -> {
            type.values.map { value ->
               val item = CompletionItem(value)
               item.kind = CompletionItemKind.EnumMember
               item.detail = type.className
               item
            }
         }

         is HoconSchemaProvider.PropertyType.Primitive -> {
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

   private fun getDefaultSnippet(property: HoconSchemaProvider.PropertySchema): String {
      return when (property.type) {
         is HoconSchemaProvider.PropertyType.Primitive -> {
            if (property.defaultValue != null) {
               "${property.name}: \"${property.defaultValue}\""
            } else {
               "${property.name}: \"\$1\""
            }
         }

         is HoconSchemaProvider.PropertyType.Object -> {
            "${property.name}: {\n  \$1\n}"
         }

         is HoconSchemaProvider.PropertyType.Map -> {
            "${property.name}: {\n  \$1\n}"
         }

         is HoconSchemaProvider.PropertyType.ListType -> {
            "${property.name}: [\n  \$1\n]"
         }

         is HoconSchemaProvider.PropertyType.Enum -> {
            "${property.name}: \$1"
         }
      }
   }

   private fun buildDocumentation(property: HoconSchemaProvider.PropertySchema): String {
      val parts = mutableListOf<String>()
      parts.add("Type: ${schemaProvider.getTypeDescription(property.type)}")

      if (property.isNullable) {
         parts.add("(optional)")
      }

      if (property.defaultValue != null) {
         parts.add("Default: ${property.defaultValue}")
      }

      return parts.joinToString("\n")
   }

   private fun analyzeContext(lines: List<String>, position: Position): CompletionContext {
      if (position.line >= lines.size) {
         return CompletionContext.TopLevel
      }

      val currentLine = lines[position.line]
      val beforeCursor = currentLine.substring(0, position.character.coerceAtMost(currentLine.length))

      // Simple heuristic-based context detection
      val trimmed = beforeCursor.trim()

      // Try to determine the property path by looking at previous lines
      val propertyPath = extractPropertyPath(lines, position)
      if (propertyPath.isEmpty()) {
         return CompletionContext.TopLevel
      }

      // Check if we're completing a value (after colon)
      if (beforeCursor.contains(':')) {
         val afterColon = beforeCursor.substringAfterLast(':').trim()
         val propertySchema = schemaProvider.getPropertySchema(propertyPath)

         // Check if this is a map-like structure where we're typing keys
         if (propertySchema?.type is HoconSchemaProvider.PropertyType.Map) {
            return CompletionContext.MapKey(propertyPath, afterColon)
         }

         return CompletionContext.Value(propertyPath, afterColon)
      }

      val parentPath = propertyPath.substringBeforeLast('.', "")

      // TODO :  This needs filling out
      return when {
//          parentPath.isNotEmpty() -> {
//              schemaProvider.getPropertySchema(parentPath)?.let { parentSchema ->
//                 CompletionContext.ObjectProperty(parentSchema.type, trimmed)
//              } ?: schemaProvider.getPropertySchema(propertyPath)
//          }
         propertyPath.isNotEmpty() -> {
            val propertySchema = schemaProvider.getPropertySchema(propertyPath)
            if (propertySchema != null) {
               CompletionContext.MapKey(propertySchema.name, trimmed)
            } else {
               CompletionContext.Unknown
            }
         }
          else -> {
             CompletionContext.Unknown
          }
      }

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
            val propertyName = line.substringBefore(':').substringBefore('{').trim()
            if (propertyName.isNotEmpty() && !propertyName.contains('}')) {
               path.add(0, propertyName.trim('"'))
               currentIndent = indent
            }
         }
      }

      return path.joinToString(".")
   }

   private fun getIndentLevel(line: String): Int {
      return line.takeWhile { it.isWhitespace() }.length
   }

   sealed class CompletionContext {
      object TopLevel : CompletionContext()
      data class ObjectProperty(val objectType: HoconSchemaProvider.PropertyType.Object, val prefix: String) :
         CompletionContext()

      data class MapKey(val mapName: String, val prefix: String) : CompletionContext()
      data class Value(val propertyPath: String, val prefix: String) : CompletionContext()
      object Unknown : CompletionContext()
   }
}

package lang.taxi.converters.swagger

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.annotations.VisibleForTesting
import lang.taxi.types.PrimitiveType
import org.apache.commons.lang3.text.WordUtils
import java.io.InputStream

class SwaggerConverter() {
   val additionalTypes = mutableMapOf<String, String>()

   fun toTaxiTypes(swaggerApi: InputStream): String {
      val swagger = ObjectMapper().readValue(swaggerApi, Map::class.java)
      val definitions: JsonMap = swagger["definitions"] as JsonMap? ?: emptyMap()
      val taxiDef = mapDefinitionsToTypes(definitions)
      val additionalTypeStrings = additionalTypes.values.joinToString("\n")
      return taxiDef + "\n" + additionalTypeStrings
   }

   @VisibleForTesting
   internal fun mapDefinitionsToTypes(types: JsonMap): String {
      return types.map { (typename, def) ->
         mapToType(typename, def as JsonMap)
      }.joinToString("\n")
   }

   @VisibleForTesting
   internal fun mapToType(typename: String, def: JsonMap): String {
      val fieldDeclarations = convertProperties(def["properties"] as JsonMap, def["required"] as List<String>? ?: emptyList())
      val comment = generateComment(def).trim()
      val formattedFieldBlock = indentLines(fieldDeclarations)
      val type = """
$comment
type $typename {
$formattedFieldBlock
}""".trim() + "\n"
      return type
   }

   private fun indentLines(lines: List<String>, separator: String = "\n") = lines.flatMap { it.split("\n") }.map { "   $it" }.joinToString(separator)

   @VisibleForTesting
   internal fun convertProperties(properties: JsonMap, requiredProperties:List<String> = emptyList()): List<String> {
      return properties.map { (propName, propDef) ->
         val propDefMap = propDef as JsonMap
         var type = when {
            propDefMap["type"] == "string" && propDef["format"] == "date" -> PrimitiveType.LOCAL_DATE.declaration
            propDefMap["type"] == "string" && propDef.containsKey("enum") -> writeEnum(propName, propDef)
            propDefMap["type"] == "string" -> PrimitiveType.STRING.declaration
            propDefMap["type"] == "int32" -> PrimitiveType.INTEGER.declaration
            else -> PrimitiveType.STRING.declaration
         }
         if (!requiredProperties.contains(propName)) type += "?"
         val comment = generateComment(propDefMap)
         comment + "$propName : $type"
      }
   }

   private fun generateComment(jsonMap: JsonMap): String {
      if (jsonMap.containsKey("description")) {
         val description = jsonMap["description"] as String
         return """
/**
 * $description
 */
"""
      } else {
         return ""
      }
   }

   private fun writeEnum(propName: String, propDef: JsonMap): String {
      val enumName = WordUtils.capitalize(propName)
      val values = propDef["enum"] as List<String>
      val enumType = """enum $enumName {
${indentLines(values, separator = ",\n")}
}"""
      additionalTypes.put(enumName, enumType)
      return enumName
   }


}

typealias JsonMap = Map<String, Any>

package lang.taxi.writers

/**
 * Fairly crude string manipulation of Hocon maps.
 */
class HoconPrettifier {
   companion object {
      fun format(src: String): String {
         return splitMapsAcrossLines(src)
      }

      /**
       * Looks for single-line maps, and converts them to multi-line maps
       * eg:
       * {"@orbital/config"="orbital/config/.conf", "@orbital/nebula"="orbital/nebula/.nebula.kts"}
       * becomes
       * {
       *    "@orbital/config": "orbital/config/.conf",
       *    "@orbital/nebula": "orbital/nebula/.nebula.kts"
       *  }
       */
      private fun splitMapsAcrossLines(src: String): String {
         return src.lines()
            .map { line ->
               val containsMap = line.contains("{") && line.trim().endsWith("}")
               if (!containsMap) return@map line

               // Append newline at map start
               val mapStart = line.indexOf('{')
               val lineBeforeMap = line.substring(0, mapStart + 1)
               val mapEnd = line.lastIndexOf("}")
               val mapPart = line.substring(mapStart + 1, mapEnd)

               // Split the map across multiple lines, with a comma, and prepend an indent to each line
               val formattedMap = mapPart.split(",").joinToString(",\n") { it.trim().replace("\"=\"", "\" : \"").prependIndent("  ") }

               listOf(lineBeforeMap, formattedMap, "}")
                  .joinToString("\n")

            }
            .joinToString("\n")
      }
   }
}

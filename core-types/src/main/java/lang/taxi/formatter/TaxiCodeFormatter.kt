package lang.taxi.formatter

object TaxiCodeFormatter {
   fun format(source: String, options: TaxiFormattingOptions = TaxiFormattingOptions.DEFAULT): String {
      val indent = if (options.insertSpaces) {
         (0 until options.tabSize).joinToString(separator = "") { " " }
      } else {
         "\t"
      }
      var indentationLevel = 0

      fun currentIndentText(): String {
         return (0 until indentationLevel).joinToString(separator = "") { indent }
      }


      val lines = source.lines().toMutableList()
      if (lines.isEmpty()) {
         return ""
      }

      val formatted = lines.mapIndexed { lineNumber, line ->
         val lineWithoutComments = line.substringBefore("//").trim()
         var appendEmptyLine = false
         if (lineWithoutComments.endsWith("}")) {
            indentationLevel = (indentationLevel - 1).coerceAtLeast(0);
            if (lines.size > lineNumber + 1 && lines[lineNumber + 1].trim() != "") {
               appendEmptyLine = true
            }
         }
         val builder = StringBuilder()
         builder.append(line.trim().prependIndent(currentIndentText()))
         if (appendEmptyLine) {
            builder.append("\n")
         }



         if (lineWithoutComments.endsWith("{")) {
            indentationLevel++
         }

         builder.toString()
      }.joinToString("\n")
      return formatted

   }
}

data class TaxiFormattingOptions(val tabSize: Int = 3, val insertSpaces: Boolean = false) {
   companion object {
      val DEFAULT = TaxiFormattingOptions()
   }
}

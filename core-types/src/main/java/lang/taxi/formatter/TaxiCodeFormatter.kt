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
         val openingBraces = lineWithoutComments.count { it == '{' }
         val closingBraces = lineWithoutComments.count { it == '}' }
         val indentationDelta = when {
            openingBraces == closingBraces -> 0
            openingBraces > closingBraces -> 1
            else -> -1
         }
         if (indentationDelta == -1) {
            indentationLevel = (indentationLevel + indentationDelta).coerceAtLeast(0)
            if (lines.size > lineNumber + 1 && lines[lineNumber + 1].trim() != "") {
               appendEmptyLine = true
            }
         }
         val builder = StringBuilder()
         builder.append(line.trim().prependIndent(currentIndentText()))
         if (appendEmptyLine) {
            builder.append("\n")
         }

         if (indentationDelta == 1) {
            indentationLevel++
         }

         builder.toString()
      }.joinToString("\n")
      return formatted

   }
}

data class TaxiFormattingOptions(val tabSize: Int = 3, val insertSpaces: Boolean = true) {
   companion object {
      val DEFAULT = TaxiFormattingOptions()
   }
}

package lang.taxi.codegen

import lang.taxi.utils.trimEmptyLines

object InlineFiles {
   const val PREFIX = "//@file:"
   fun getFilenameFromComment(inlineSource: String, default: String = "Unknown source"): String {
      val firstLine = inlineSource.trimEmptyLines()
         .lineSequence()
         .firstOrNull()
         ?.trim()

      return if (firstLine != null && firstLine.startsWith(PREFIX)) {
         firstLine.removePrefix(PREFIX)
            .trim()
      } else {
         return default
      }
   }
}

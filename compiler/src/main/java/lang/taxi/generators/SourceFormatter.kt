package lang.taxi.generators

import lang.taxi.utils.log


/**
 * Source formatter and modifier.
 * Used for converting generated taxi into something that is demoable.
 * Note that if you enable the mutating methods (inlineTypeAliases, stripNamespaces), then
 * the resulting code may look nicer, but is unlikely to compile.
 *
 * At this stage, generating pretty, compilable code is not a goal, though would be nice
 * and will be implemented at a later date.
 */
class SourceFormatter(private val spaceCount: Int = 3, private val inlineTypeAliases: Boolean = false) {
   companion object {
      private const val REMOVED_TOKEN = "REMOVED_TOKEN"
   }

   fun format(raw: String): String {
      val unindented = raw.split("\n")
         .map { it.trim() }

      var indentationDepth = 0
      var isInsideDocumentationBlock = false
      val indented = unindented.joinToString("\n") { line ->
         val lineStartsDocumentationBlock = line.startsWith("[[")
         val lineEndsDocumentationBlock = line.endsWith("]]")
         val lineIsLineComment = line.startsWith("//")

         if (lineStartsDocumentationBlock) {
            isInsideDocumentationBlock = true
         }
         if (lineEndsDocumentationBlock) {
            isInsideDocumentationBlock = false
         }

         if (!isInsideDocumentationBlock && line.endsWith("}")) indentationDepth--

         if (indentationDepth < 0) {
            log().warn("Source formatter has generated an incorrect indentationDepth when formatting: $raw")
            indentationDepth = 0
         }

         val indented = line.prependIndent(" ".repeat(indentationDepth * spaceCount))

         if (!isInsideDocumentationBlock && line.endsWith("{")) indentationDepth++
         indented
      }

      return if (inlineTypeAliases) {
         inlineTypeAliases(indented)
      } else {
         indented
      }
   }

   private fun stripNamespaces(raw: String): String {
      TODO();
   }

   private fun inlineTypeAliases(raw: String): String {

      val lines = raw.split("\n").toMutableList()
      lines.forEachIndexed { index, line ->
         if (line.trim().startsWith("type alias")) {
            if (inlineTypeAliasLine(line, lines)) {
               lines[index] = REMOVED_TOKEN
            }
         }
      }
      return lines.filter { it != REMOVED_TOKEN }.joinToString("\n")
   }

   fun inlineTypeAliasLine(line: String, lines: MutableList<String>): Boolean {
      val (aliasName, aliasLine) = extractAlias(line)
      var successful = false
      lines.forEachIndexed { index, content ->
         if (content.replace(" ", "").endsWith(":$aliasName")) {
            lines[index] = "$content $aliasLine"
            successful = true
            return@forEachIndexed
         }
      }
      return successful
   }

   private fun extractAlias(line: String): Pair<String, String> {
      val aliasDeclaration = line.trim().removePrefix("type alias ").trim()
      val aliasName = aliasDeclaration.split(" ").first()
      return aliasName to aliasDeclaration.removePrefix(aliasName).trim()
   }
}

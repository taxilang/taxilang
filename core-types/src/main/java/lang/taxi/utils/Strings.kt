package lang.taxi.utils

fun String.prependIfAbsent(prefix: String): String {
   return if (this.startsWith(prefix)) {
      this
   } else {
      prefix + this
   }
}


fun String.trimEmptyLines(preserveIndent: Boolean = false): String {
   return this.lineSequence()
      .filter { it.trim().isNotEmpty() }
      .joinToString("\n") {
         if (preserveIndent) {
            it
         } else it.trim()
      }
}

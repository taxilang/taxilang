package lang.taxi.utils

fun String.prependIfAbsent(prefix:String):String {
   return if (this.startsWith(prefix)) {
      this
   } else {
      prefix + this
   }
}


fun String.trimEmptyLines():String {
   return this.lineSequence()
      .filter { it.trim().isNotEmpty() }
      .joinToString("\n") { it.trim() }
}

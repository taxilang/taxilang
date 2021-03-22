package lang.taxi.utils

fun String.quoted(quoteChar:String = "\""): String {
   return quoteChar + this + quoteChar
}
fun String.quotedIfNotAlready(quoteChar:String = "\"", quoteToReplace:String? = null): String {
   return when {
       this.startsWith(quoteChar) && this.endsWith(quoteChar) -> {
          this
       }
      quoteToReplace != null && this.startsWith(quoteToReplace) && this.endsWith(quoteToReplace) -> {
         this.removeSurrounding(quoteToReplace).quoted(quoteChar)
      }
       else -> {
          this.quoted(quoteChar)
       }
   }
}
fun Any.quotedIfNecessary(quoteChar:String = "\"", quoteToReplace:String? = null): String {
   return when(this) {
      is String -> this.quotedIfNotAlready(quoteChar, quoteToReplace)
      is Boolean -> this.toString()
      is Number -> this.toString()
      else -> this.toString()
   }
}

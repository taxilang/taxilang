package lang.taxi.utils

fun String.quoted(): String {
   return "\"$this\""
}
fun String.quotedIfNotAlready(): String {
   return if (this.startsWith('"') && this.endsWith('"')) {
      this
   } else {
      this.quoted()
   }
}
fun Any.quotedIfString(): String {
   return when(this) {
      is String -> this.quoted()
      is Number -> this.toString()
      else -> this.toString()
   }
}

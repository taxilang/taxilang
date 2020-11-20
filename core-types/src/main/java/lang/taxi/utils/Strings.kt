package lang.taxi.utils

fun String.prependIfAbsent(prefix:String):String {
   return if (this.startsWith(prefix)) {
      this
   } else {
      prefix + this
   }
}

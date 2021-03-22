package lang.taxi.query.jpa

fun String.withoutWhitespace(): String {
   return this
      .lines()
      .map { it.trim().replace(" ","") }
      .filter { it.isNotEmpty() }
      .joinToString("")
}

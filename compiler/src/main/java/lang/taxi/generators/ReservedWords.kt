package lang.taxi.generators

import lang.taxi.ReservedWords

private val validIdentifier = "^[A-Za-z\$_][a-zA-Z0-9\$_]+\$".toRegex()
private fun String.isReservedWord() = ReservedWords.words.contains(this)
private fun String.isValidIdentifier() = this.matches(validIdentifier)

fun String.reservedWordEscaped(): String {
   val isEscaped = this.startsWith("`") && this.endsWith("`")
   return when {
      isEscaped -> this
      this.isReservedWord() || !this.isValidIdentifier() -> return "`$this`"
      else -> return this
   }
}

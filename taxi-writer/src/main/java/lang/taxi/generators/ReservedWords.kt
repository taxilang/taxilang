package lang.taxi.generators

import lang.taxi.ReservedWords

private val validIdentifier = "^[A-Za-z\$_][a-zA-Z0-9\$_]+\$".toRegex()
private fun String.isReservedWord() = ReservedWords.words.contains(this)
private fun String.isValidIdentifier() = this.matches(validIdentifier)

fun String.reservedWordEscaped(): String {
    return if (this.isReservedWord() || !this.isValidIdentifier()) {
        "`$this`"
    } else {
        this
    }
}

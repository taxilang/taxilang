package lang.taxi.generators

object ReservedWords {
    val words = listOf("type", "service", "alias")

}

fun String.reservedWordEscaped(): String {
    return if (ReservedWords.words.contains(this)) {
        "`$this`"
    } else {
        this
    }
}
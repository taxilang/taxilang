package lang.taxi.generators

data class GeneratedTaxiCode(val taxi: List<String>, val messages: List<Message>)
data class Message(val level: Level, val message: String, val link: String? = null)
enum class Level {
    INFO,
    WARN,
    ERROR;
}

class Logger {
    private val _messages: MutableList<Message> = mutableListOf()

    val messages: List<Message>
        get() {
            return _messages.toList()
        }

    fun info(message: String, link:String? = null) = append(Level.INFO, message, link)
    fun warn(message: String, link:String? = null) = append(Level.WARN, message, link)
    fun error(message: String, link:String? = null) = append(Level.ERROR, message, link)

    private fun append(level: Level, message: String, link: String?) {
        _messages.add(Message(level, message, link))
    }
}


package lang.taxi.logging

class CompositeLogger(loggers: List<MessageLogger> = emptyList()) : MessageLogger {
   private val loggers = loggers.toMutableList()

   fun addLogger(logger: MessageLogger) = loggers.add(logger)
   override fun info(message: String) {
      loggers.forEach { it.info(message) }
   }

   override fun error(message: String) {
      loggers.forEach { it.error(message) }
   }

   override fun warn(message: String) {
      loggers.forEach { it.warn(message) }
   }

}

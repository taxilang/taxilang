package lang.taxi.packages

import org.slf4j.LoggerFactory

/**
 * A simple interface for providing updates about download progress
 * back to tooling.
 *
 * Not intended to be a full log implementation.
 */
interface MessageLogger {
   fun info(message: String)
   fun error(message: String)
   fun warn(message: String)
}

object LogWritingMessageLogger : MessageLogger {
   private val log = LoggerFactory.getLogger("Package manager")
   override fun info(message: String) {
      log.info(message)
   }

   override fun error(message: String) {
      log.error(message)
   }

   override fun warn(message: String) {
      log.warn(message)
   }

}

class CompositeLogger(private val loggers: List<MessageLogger>) : MessageLogger {
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

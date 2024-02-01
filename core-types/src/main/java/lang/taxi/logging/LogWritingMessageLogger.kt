package lang.taxi.logging

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Implementation of MessageLogger which outputs to log()
 */
class LogWritingMessageLogger(
   private val log: Logger = LoggerFactory.getLogger("lang.taxi.PackageManager")
) : MessageLogger {
   constructor(logger: String) : this(LoggerFactory.getLogger(logger))
   constructor(logger: Class<*>) : this(LoggerFactory.getLogger(logger))

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

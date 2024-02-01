package lang.taxi.lsp.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.services.LanguageClient
import org.slf4j.LoggerFactory

/**
 * An SLF4J appender that forwards log messages through to
 * the LSP language client.
 *
 * These messages are visible to end users
 */
class LspClientLogAppender(val client: LanguageClient) : AppenderBase<ILoggingEvent>() {
   override fun append(event: ILoggingEvent) {
      val messageType: MessageType? = when (event.level) {
         Level.DEBUG -> MessageType.Log
         Level.INFO -> MessageType.Info
         Level.WARN -> MessageType.Warning
         Level.ERROR -> MessageType.Error
         else -> null
      } ?: return // Ignore anything that was null

      val messageParams = MessageParams(messageType, event.message)
      client.logMessage(messageParams)
   }

   companion object {
      fun installFor(client: LanguageClient, loggers: List<Pair<String, Level>>) {
         client.logMessage(
            MessageParams(
               MessageType.Info,
               "Hello?"
            )
         )
         val context = LoggerFactory.getILoggerFactory() as LoggerContext
         val appender = LspClientLogAppender(client)

         loggers.forEach { (loggerName, level) ->
            val logger = context.getLogger(loggerName)
            logger.level = level
            logger.addAppender(appender)
            logger.isAdditive = false
         }

      }
   }
}

package lang.taxi.lsp

import lang.taxi.logging.MessageLogger
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.services.LanguageClient

class LspClientMessageLogger(private val client: LanguageClient) : MessageLogger {
    override fun info(message: String) {
        client.logMessage(
            MessageParams(MessageType.Info, message)
        )
    }

    override fun error(message: String) {
        client.logMessage(
            MessageParams(MessageType.Error, message)
        )
    }

    override fun warn(message: String) {
        client.logMessage(
            MessageParams(MessageType.Warning, message)
        )
    }
}

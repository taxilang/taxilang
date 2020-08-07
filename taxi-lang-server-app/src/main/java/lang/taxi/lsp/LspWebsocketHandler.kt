package lang.taxi.lsp

import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint
import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageServer
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

// WIP, DOESN'T WORK

class LspWebsocketHandler() : TextWebSocketHandler() {
    var remoteEndpoint: RemoteEndpoint
    var jsonHandler: MessageJsonHandler
    var session: WebSocketSession? = null

    init {
        // Code inspired by this post:
        // https://stackoverflow.com/questions/50874473/springboot-get-inputstream-and-outputstream-from-websocket

        val languageServer = TaxiLanguageServer()
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap()
        supportedMethods.putAll(ServiceEndpoints.getSupportedMethods(LanguageClient::class.java))
        supportedMethods.putAll(ServiceEndpoints.getSupportedMethods(LanguageServer::class.java))

        jsonHandler = MessageJsonHandler(supportedMethods)

        remoteEndpoint = RemoteEndpoint(MessageConsumer { message ->
            val message = jsonHandler.serialize(message)
            session?.let { it.sendMessage(TextMessage(message)) }
        }, ServiceEndpoints.toEndpoint(languageServer))

        val proxy = ServiceEndpoints.toServiceObject(remoteEndpoint, LanguageClient::class.java)
        languageServer.connect(proxy)

        jsonHandler.methodProvider = remoteEndpoint

    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        this.session = session
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        remoteEndpoint.consume(jsonHandler.parseMessage(message.payload));
    }


}

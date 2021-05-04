package lang.taxi.lsp

import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint
import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageServer
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

// WIP, DOESN'T WORK

class LspWebsocketHandler(val simpMessagingTemplate: SimpMessagingTemplate) : TextWebSocketHandler() {
    init {
        // Code inspired by this post:
        // https://stackoverflow.com/questions/50874473/springboot-get-inputstream-and-outputstream-from-websocket

        val languageServer = TaxiLanguageServer()

        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap()
        supportedMethods.putAll(ServiceEndpoints.getSupportedMethods(LanguageClient::class.java))
        supportedMethods.putAll(ServiceEndpoints.getSupportedMethods(LanguageServer::class.java))
//        supportedMethods.putAll(languageServer.supportedMethods())
        val jsonHandler = MessageJsonHandler(supportedMethods)

        val remoteEndpoint = RemoteEndpoint(MessageConsumer { message ->
            simpMessagingTemplate.convertAndSendToUser("user", "/lang/message",
                    jsonHandler.serialize(message))
        }, ServiceEndpoints.toEndpoint(languageServer))
        jsonHandler.methodProvider = remoteEndpoint

    }


}

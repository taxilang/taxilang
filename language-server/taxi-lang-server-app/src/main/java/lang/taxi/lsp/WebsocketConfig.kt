package lang.taxi.lsp

import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@EnableWebSocket
@Configuration
class WebsocketConfig : WebSocketConfigurer{
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        TODO("Not yet implemented")
    }

}
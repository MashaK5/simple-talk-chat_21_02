package ru.senin.kotlin.net.server

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.cio.websocket.*
import io.ktor.jackson.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.websocket.*
import org.slf4j.event.Level
import ru.senin.kotlin.net.Message

class WebsocketChatServer(host: String, port: Int): ChatServer(host, port, "websocket-chat") {
    override fun configureModule(): Application.() -> Unit = {
        install(CallLogging) {
            level = Level.DEBUG
            filter { call -> call.request.path().startsWith("/") }
        }

        install(WebSockets)

        install(DefaultHeaders) {
            header("X-Engine", "Ktor")
        }

        install(ContentNegotiation) {
            jackson {
                enable(SerializationFeature.INDENT_OUTPUT)
            }
        }

        routing {
            webSocket("/v1/ws/message") {
                for (frame in incoming) {
                    frame as? Frame.Text ?: continue
                    val receivedText: String = frame.readText()
                    val message: Message = objectMapper.readValue(receivedText)
                    listener?.messageReceived(message.user, message.text)
                }
            }

            webSocket("/") {  }
        }
    }
}

package ru.senin.kotlin.net.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.*
import io.ktor.client.features.websocket.*
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import ru.senin.kotlin.net.Message

class WebsocketChatClient(host: String, port: Int): ChatClient {
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val client: HttpClient = HttpClient { install(WebSockets) }
    private val channel = Channel<Message>(UNLIMITED)  // Used not to connect with every message

    init {
        @Suppress("BlockingMethodInNonBlockingContext")
        GlobalScope.launch {
            client.ws(host = host, port = port, path = "/v1/ws/message") {
                for (message in channel) {
                    if (outgoing.isClosedForSend)
                        println("Cannot send message to $host:$port")
                    else {
                        val serializedMessage: String = objectMapper.writeValueAsString(message)
                        outgoing.send(Frame.Text(serializedMessage))
                    }
                }
            }
        }
    }

    override fun sendMessage(message: Message) {
        runBlocking {
            channel.send(message)
        }
    }
}

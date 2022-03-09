package ru.senin.kotlin.net.server

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.jackson.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.request.*
import io.ktor.routing.*
import kotlinx.coroutines.*
import org.slf4j.event.Level
import ru.senin.kotlin.net.Message
import java.net.InetSocketAddress

class UDPChatServer(host: String, port: Int): ChatServer(host, port, "udp-server") {
    private val socket = aSocket(ActorSelectorManager(Dispatchers.IO)).udp().bind(InetSocketAddress(host, port))
    private lateinit var serverJob: Job

    override fun configureModule(): Application.() -> Unit = {
        install(CallLogging) {
            level = Level.DEBUG
            filter { call -> call.request.path().startsWith("/") }
        }

        install(DefaultHeaders) {
            header("X-Engine", "Ktor")
        }

        install(ContentNegotiation) {
            jackson {
                enable(SerializationFeature.INDENT_OUTPUT)
            }
        }

        routing {
            runBlocking {
                serverJob = launch {
                    socket.use {
                        for (datagram in socket.incoming) {
                            val text: String = datagram.packet.readText()
                            val message: Message = objectMapper.readValue(text)
                            listener?.messageReceived(message.user, message.text)
                        }
                    }
                }
            }
        }
    }

    override fun stop() {
        runBlocking {
            serverJob.cancelAndJoin()
        }
        super.stop()
    }
}

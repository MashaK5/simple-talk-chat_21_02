package ru.senin.kotlin.net.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.cio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import ru.senin.kotlin.net.Message
import java.net.InetSocketAddress
import java.net.SocketException

class UDPChatClient(private val host: String, private val port: Int): ChatClient {
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    override fun sendMessage(message: Message) {
        val serializedMessage: String = objectMapper.writeValueAsString(message)
        runBlocking {
            while (true) {
                try {
                    aSocket(ActorSelectorManager(Dispatchers.IO)).udp().connect(InetSocketAddress(host, port))
                            .openWriteChannel(true).write(serializedMessage)
                    break
                } catch (e: SocketException) {
                    delay(50)
                }
            }
        }
    }
}

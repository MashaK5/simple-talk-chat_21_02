package ru.senin.kotlin.net

import com.apurebase.arkenv.Arkenv
import com.apurebase.arkenv.argument
import com.apurebase.arkenv.parse
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import ru.senin.kotlin.net.server.ChatServer
import ru.senin.kotlin.net.server.HttpChatServer
import ru.senin.kotlin.net.server.UDPChatServer
import ru.senin.kotlin.net.server.WebsocketChatServer
import java.lang.IllegalArgumentException
import java.net.URL
import kotlin.concurrent.thread

class Parameters: Arkenv() {
    val name: String by argument("--name") {
        description = "Name of user"
        validate("Incorrect name.") { checkUserName(it) }
    }

    val registryBaseUrl: String by argument("--registry") {
        description = "Base URL of User Registry"
        defaultValue = { "http://localhost:8088" }
    }

    val protocol: Protocol by argument("--protocol") {
        description = "Protocol to use"
        defaultValue = { Protocol.HTTP }
        mapping = { when (it) {
            "HTTP" -> Protocol.HTTP
            "WEBSOCKET" -> Protocol.WEBSOCKET
            "UDP" -> Protocol.UDP
            else -> throw IllegalArgumentException("Invalid protocol.")
        } }
    }

    val host: String by argument("--host") {
        description = "Hostname or IP to listen on"
        defaultValue = { "0.0.0.0" } // 0.0.0.0 - listen on all network interfaces
        validate("Incorrect IP or Host.") {
            Regex("^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.)" +  // IP
                    "{3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\$").matches(it) ||
                    Regex("^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*" +  // Hostname
                            "([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9])\$").matches(it)
        }
    }

    val port: Int? by argument("--port") {
        description = "Port to listen to"
        validate("Incorrect port number.") { it in 1..65535 }
    }

    val publicUrl: String? by argument("--public-url") {
        description = "Public URL"
    }
}

val log: Logger = LoggerFactory.getLogger("main")
lateinit var parameters: Parameters

fun main(args: Array<String>) {
    try {
        parameters = Parameters().parse(args)
        if (parameters.help) {
            println(parameters.toString())
            return
        }

        val protocol: Protocol = parameters.protocol
        val host: String = parameters.host
        val port: Int = parameters.port ?: when (protocol) {
            Protocol.HTTP -> 8080
            Protocol.WEBSOCKET -> 8082
            Protocol.UDP -> 3000
        }
        val name: String = parameters.name

        // initialize registry interface
        val objectMapper: ObjectMapper = jacksonObjectMapper()
        val registry: RegistryApi = Retrofit.Builder()
            .baseUrl(parameters.registryBaseUrl)
            .addConverterFactory(JacksonConverterFactory.create(objectMapper))
            .build().create(RegistryApi::class.java)

        // create server engine
        val server: ChatServer = when (protocol) {
            Protocol.HTTP -> HttpChatServer(host, port)
            Protocol.WEBSOCKET -> WebsocketChatServer(host, port)
            Protocol.UDP -> UDPChatServer(host, port)
        }
        val chat = Chat(name, registry)
        server.setMessageListener(chat)

        // start server as separate job
        val serverJob: Thread = thread {
            server.start()
        }
        try {
            // register our client
            val userAddress: UserAddress = when {
                parameters.publicUrl != null -> {
                    val url = URL(parameters.publicUrl)
                    UserAddress(protocol, url.host, if (url.port == -1) port else url.port)
                }
                else -> UserAddress(protocol, host, port)
            }
            registry.register(UserInfo(name, userAddress)).execute()
            chat.commandLoop()
        } finally {
            registry.unregister(name).execute()
            server.stop()
            serverJob.join()
        }
    } catch (e: Exception) {
        log.error("Error! ${e.message}", e)
        println("Error! ${e.message}")
    }
}

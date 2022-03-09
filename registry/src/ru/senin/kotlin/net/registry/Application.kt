package ru.senin.kotlin.net.registry

import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.application.*
import io.ktor.client.features.websocket.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.netty.*
import kotlinx.coroutines.delay
import org.slf4j.event.Level
import ru.senin.kotlin.net.Protocol
import ru.senin.kotlin.net.UserAddress
import ru.senin.kotlin.net.UserInfo
import ru.senin.kotlin.net.checkUserName
import java.util.concurrent.ConcurrentHashMap
import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val log: Logger = LoggerFactory.getLogger("Application")

object Registry {
    val users = ConcurrentHashMap<String, UserAddress>()
}

val client = HttpClient {  // Client for checking health of users
    install(WebSockets)
}

suspend fun checkUserHealth(userAddress: UserAddress): Boolean {
    try {
        return when (userAddress.protocol) {
            Protocol.HTTP -> {
                val response: String = client.get("$userAddress/v1/health")
                response == "OK"
            }
            Protocol.WEBSOCKET -> {
                client.ws(host = userAddress.host, port = userAddress.port) { }
                true
            }
            Protocol.UDP -> {
                // TODO: Add checking health for UDP
                true
            }
        }
    } catch (e: Exception) {
        return false
    }
}

suspend fun runCheckingHealth(timeout: Long = 120000L) {
    log.info("Periodic checking users started with timeout $timeout ms")
    var nonRespondingUsers = mutableMapOf<String, Int>()
    while (true) {
        delay(timeout)
        for ((userName, userAddress) in Registry.users) {
            if (checkUserHealth(userAddress)) {
                nonRespondingUsers.remove(userName)
            } else {
                println(userName)
                nonRespondingUsers[userName] = nonRespondingUsers.getOrDefault(userName, 0) + 1
            }
        }
        nonRespondingUsers.filter { it.value > 3 }.forEach { Registry.users.remove(it.key) }
        nonRespondingUsers = nonRespondingUsers.filter { it.value <= 3 }.toMutableMap()
        log.debug("Active users: ${Registry.users}")
    }
}

fun main(args: Array<String>) {
    GlobalScope.launch {
        runCheckingHealth()
    }
    EngineMain.main(args)
}

@Suppress("UNUSED_PARAMETER")
@JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }

    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }
    install(StatusPages) {
        exception<IllegalArgumentException> { cause ->
            call.respond(HttpStatusCode.BadRequest, cause.message ?: "invalid argument")
        }
        exception<UserAlreadyRegisteredException> { cause ->
            call.respond(HttpStatusCode.Conflict, cause.message ?: "user already registered")
        }
        exception<IllegalUserNameException> { cause ->
            call.respond(HttpStatusCode.BadRequest, cause.message ?: "illegal user name")
        }
    }
    routing {
        get("/v1/health") {
            call.respondText("OK", contentType = ContentType.Text.Plain)
        }

        post("/v1/users") {
            val user = call.receive<UserInfo>()
            val name = user.name
            if (!checkUserName(name))
                throw IllegalUserNameException()
            if (Registry.users.containsKey(name)) {
                throw UserAlreadyRegisteredException()
            }
            Registry.users[name] = user.address
            call.respond(mapOf("status" to "ok"))
        }

        get("/v1/users") {
            call.respond(Registry.users)
        }

        put("/v1/users/{name}") {
            val name = call.parameters["name"] ?: throw IllegalUserNameException()
            val address = call.receive<UserAddress>()
            if (!Registry.users.containsKey(name) && !checkUserName(name)) {
                throw IllegalUserNameException()
            }
            Registry.users[name] = address
            call.respond(mapOf("status" to "ok"))
        }

        delete("/v1/users/{name}") {
            Registry.users.remove(call.parameters["name"])
            call.respond(mapOf("status" to "ok"))
        }
    }
}

class UserAlreadyRegisteredException: RuntimeException("User already registered")
class IllegalUserNameException: RuntimeException("Illegal user name")

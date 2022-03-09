package ru.senin.kotlin.net.server

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.slf4j.LoggerFactory

interface ChatMessageListener {
    fun messageReceived(userName: String, text: String)
}

abstract class ChatServer(private val host: String, private val port: Int, private val serverName: String) {
    protected val objectMapper = jacksonObjectMapper()
    protected var listener: ChatMessageListener? = null
    private val engine = createEngine()

    fun setMessageListener(listener: ChatMessageListener) {
        this.listener = listener
    }

    private fun createEngine(): NettyApplicationEngine {
        val applicationEnvironment = applicationEngineEnvironment {
            log = LoggerFactory.getLogger(serverName)
            classLoader = ApplicationEngineEnvironment::class.java.classLoader
            connector {
                this.host = this@ChatServer.host
                this.port = this@ChatServer.port
            }
            module(configureModule())
        }
        return NettyApplicationEngine(applicationEnvironment)
    }

    abstract fun configureModule(): Application.() -> Unit

    open fun start() {
        engine.start(true)
    }

    open fun stop() {
        engine.stop(1000, 2000)
    }
}

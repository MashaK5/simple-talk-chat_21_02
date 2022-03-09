package ru.senin.kotlin.net.client

import org.junit.jupiter.api.Test
import ru.senin.kotlin.net.Message
import ru.senin.kotlin.net.server.ChatMessageListener
import ru.senin.kotlin.net.server.HttpChatServer
import ru.senin.kotlin.net.server.UDPChatServer
import ru.senin.kotlin.net.server.WebsocketChatServer
import kotlin.concurrent.thread
import kotlin.test.assertEquals

class ChatTest {
    private val host = "0.0.0.0"
    private val port = 6666
    private val userName = "Foo"
    private val text = "Bar"

    @Test
    fun http() {
        val server = HttpChatServer(host, port)
        val testListener = object: ChatMessageListener {
            override fun messageReceived(userName: String, text: String) {
                assertEquals(this@ChatTest.userName, userName)
                assertEquals(this@ChatTest.text, text)
            }
        }
        server.setMessageListener(testListener)
        val serverJob = thread { server.start() }
        val client = HttpChatClient(host, port)
        client.sendMessage(Message(userName, text))
        server.stop()
        serverJob.join()
    }

    @Test
    fun websocket() {
        val server = WebsocketChatServer(host, port)
        val testListener = object: ChatMessageListener {
            override fun messageReceived(userName: String, text: String) {
                assertEquals(this@ChatTest.userName, userName)
                assertEquals(this@ChatTest.text, text)
            }
        }
        server.setMessageListener(testListener)
        val serverJob = thread { server.start() }
        val client = WebsocketChatClient(host, port)
        client.sendMessage(Message(userName, text))
        server.stop()
        serverJob.join()
    }

    @Test
    fun udp() {
        val server = UDPChatServer(host, port)
        val testListener = object: ChatMessageListener {
            override fun messageReceived(userName: String, text: String) {
                assertEquals(this@ChatTest.userName, userName)
                assertEquals(this@ChatTest.text, text)
            }
        }
        server.setMessageListener(testListener)
        val serverJob = thread { server.start() }
        val client = UDPChatClient(host, port)
        client.sendMessage(Message(userName, text))
        server.stop()
        serverJob.join()
    }
}

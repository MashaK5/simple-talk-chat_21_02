package ru.senin.kotlin.net.client

import ru.senin.kotlin.net.Message

interface ChatClient {
    fun sendMessage(message: Message)
}

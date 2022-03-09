package ru.senin.kotlin.net

enum class Protocol {
    HTTP,
    WEBSOCKET,
    UDP,
}

data class UserAddress(
    val protocol: Protocol,
    val host: String,
    val port: Int,
) {
    override fun toString(): String {
        val protocolName: String = when (protocol) {
            Protocol.HTTP -> "http"
            Protocol.WEBSOCKET -> "ws"
            Protocol.UDP -> "udp"
        }
        return "${protocolName}://${host}:${port}"
    }
}

data class UserInfo(val name: String, val address: UserAddress)

data class Message(val user: String, val text: String)

fun checkUserName(name: String) = """^[a-zA-Z0-9-_.]+$""".toRegex().matches(name)

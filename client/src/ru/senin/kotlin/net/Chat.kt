package ru.senin.kotlin.net

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.senin.kotlin.net.client.*
import ru.senin.kotlin.net.server.ChatMessageListener

class Chat(private val name: String, private val registry: RegistryApi): ChatMessageListener {
    private var exit = false
    private var selectedUser: String? = null
    private val clients = mutableMapOf<String, ChatClient>()
    private var users =  mutableMapOf<String, UserAddress>()

    private fun prompt(): String {
        val prompt = "  to [${selectedUser ?: "<not selected>"}] <<< "
        print(prompt)
        var value: String? = readLine()
        while (value.isNullOrBlank()) {
            print(prompt)
            value = readLine()
        }
        return value.trimStart()
    }

    private fun updateUsersList(printList: Boolean = true) {
        val registeredUsers: Map<String, UserAddress>? = registry.list().execute().getOrNull()
        if (registeredUsers == null) {
            println("Cannot get users from registry")
            return
        }
        val aliveUserNames: Set<String> = registeredUsers.keys
        if (selectedUser != null && selectedUser !in aliveUserNames) {
            println("Selected user was removed from registry")
            selectedUser = null
        }
        users = registeredUsers as MutableMap<String, UserAddress>
        clients.entries.retainAll { (userName, _) -> userName in aliveUserNames }
        if (printList) {
            users.forEach { (name, address) -> println("$name ==> $address") }
        }
    }

    private fun selectUser(userName: String) {
        val userAddress: UserAddress? = users[userName]
        if (userAddress == null) {
            println("Unknown user '$userName'")
            return
        }
        selectedUser = userName
    }

    private fun exit() {
        exit = true
    }

    private fun message(text: String) {
        val currentUser: String? = selectedUser
        if (currentUser == null) {
            println("User not selected. Use :user command")
            return
        }
        val address: UserAddress? = users[currentUser]
        if (address == null) {
            println("Cannot send message, because user disappeared")
            return
        }
        val client: ChatClient = clients.getOrPut(currentUser) {
            when (address.protocol) {
                Protocol.HTTP -> HttpChatClient(address.host, address.port)
                Protocol.WEBSOCKET -> WebsocketChatClient(address.host, address.port)
                Protocol.UDP -> UDPChatClient(address.host, address.port)
            }
        }
        try {
            client.sendMessage(Message(name, text))
        } catch (e: Exception) {
            println("Error! ${e.message}")
        }
    }

    fun commandLoop() {
        printWelcome()
        updateUsersList()
        GlobalScope.launch {  // Update list of users every 2 minutes
            while (!exit) {
                delay(120000)
                updateUsersList(printList = false)
            }
        }
        var input: String
        while (!exit) {
            input = prompt()
            when (input.substringBefore(" ")) {
                ":update" -> updateUsersList()
                ":exit" -> exit()
                ":user" -> {
                    val userName: String = input.split("""\s+""".toRegex()).drop(1).joinToString(" ")
                    selectUser(userName)
                }
                "" -> {}
                else -> message(input)
            }
        }
    }

    private fun printWelcome() {
        println(
            """
                          Был бы                      
             _______     _       _       _   __   
            |__   __|   / \     | |     | | / /   
               | |     / ^ \    | |     | |/ /    
               | |    / /_\ \   | |     |    \     
               | |   / _____ \  | |___  | |\  \    
               |_|  /_/     \_\ |_____| |_| \__\ () () ()   
                                     
                    \ | /
                ^  -  O  -  
               / \^ / | \   
              /  / \        Hi, $name
             /  /   \     Welcome to the chat, buddy!
            """.trimIndent()
        )
    }

    override fun messageReceived(userName: String, text: String) {
        println("\nfrom [$userName] >>> $text")
    }
}

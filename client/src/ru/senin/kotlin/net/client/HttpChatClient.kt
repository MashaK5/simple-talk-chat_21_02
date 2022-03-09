package ru.senin.kotlin.net.client

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import ru.senin.kotlin.net.HttpApi
import ru.senin.kotlin.net.Message

class HttpChatClient(host: String, port: Int): ChatClient {
    private val objectMapper = jacksonObjectMapper()
    private val httpApi: HttpApi = Retrofit.Builder()
        .baseUrl("http://$host:$port")
        .addConverterFactory(JacksonConverterFactory.create(objectMapper))
        .build().create(HttpApi::class.java)

    override fun sendMessage(message: Message) {
        val response = httpApi.sendMessage(message).execute()
        if (!response.isSuccessful) {
            println("{${response.code()} ${response.message()}}")
        }
    }
}

package ru.senin.kotlin.net.registry

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.application.*
import io.ktor.config.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.senin.kotlin.net.Protocol
import ru.senin.kotlin.net.UserAddress
import ru.senin.kotlin.net.UserInfo
import kotlin.test.*

fun Application.testModule() {

    (environment.config as MapApplicationConfig).apply {
        // define test environment here
    }
    module(testing = true)
}

class ApplicationTest {
    private val objectMapper = jacksonObjectMapper()
    private val testUserName = "Masha"
    private val testHttpAddress = UserAddress(Protocol.HTTP,"127.0.0.1", 9999)
    private val userData = UserInfo(testUserName, testHttpAddress)

    private val illegalTestUserName = "Маша"
    private val illegalUserData = UserInfo(illegalTestUserName, testHttpAddress)

    private val newTestHttpAddress = UserAddress(Protocol.HTTP,"127.0.0.2", 9993)

    @BeforeEach
    fun clearRegistry() {
        Registry.users.clear()
    }

    @Test
    fun `health endpoint`() {
        withTestApplication({ testModule() }) {
            handleRequest(HttpMethod.Get, "/v1/health").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals("OK", response.content)
            }
        }
    }

    @Test
    fun `register new user with correct name`() {
        withTestApplication({ testModule() }) {
            handleRequest {
                method = HttpMethod.Post
                uri = "/v1/users"
                addHeader("Content-type", "application/json")
                setBody(objectMapper.writeValueAsString(userData))
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val content = response.content ?: fail("No response content")
                val info = objectMapper.readValue<HashMap<String,String>>(content)
                assertNotNull(info["status"])
                assertEquals("ok", info["status"])

                assertTrue(testUserName in Registry.users.toMap())
            }
        }
    }

    @Test
    fun `register new user with illegal name`() {
        withTestApplication({ testModule() }) {
            handleRequest {
                method = HttpMethod.Post
                uri = "/v1/users"
                addHeader("Content-type", "application/json")
                setBody(objectMapper.writeValueAsString(illegalUserData))
            }.apply {
                assertEquals(HttpStatusCode.BadRequest, response.status())
                val content = response.content ?: fail("No response content")
                assertEquals("Illegal user name", content)

                assertFalse(testUserName in Registry.users.toMap())
            }
        }
    }

    @Test
    fun `register existing user with correct name`() {
        withRegisteredTestUser {
            handleRequest {
                method = HttpMethod.Post
                uri = "/v1/users"
                addHeader("Content-type", "application/json")
                setBody(objectMapper.writeValueAsString(userData))
            }.apply {
                assertEquals(HttpStatusCode.Conflict, response.status())
                val content = response.content ?: fail("No response content")
                assertEquals("User already registered", content)

                assertTrue(testUserName in Registry.users.toMap())
            }
        }
    }

    @Test
    fun `update exiting user`() {
        withRegisteredTestUser {
            handleRequest {
                method = HttpMethod.Put
                uri = "/v1/users/${testUserName}"
                addHeader("Content-type", "application/json")
                setBody(objectMapper.writeValueAsString(newTestHttpAddress))
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val content = response.content ?: fail("No response content")
                val info = objectMapper.readValue<HashMap<String,String>>(content)
                assertNotNull(info["status"])
                assertEquals("ok", info["status"])

                assertEquals(newTestHttpAddress, Registry.users.toMap()[testUserName])
            }
        }
    }

    @Test
    fun `update new user with correct name`() {
        withTestApplication({ testModule() }) {
            handleRequest {
                method = HttpMethod.Put
                uri = "/v1/users/${testUserName}"
                addHeader("Content-type", "application/json")
                setBody(objectMapper.writeValueAsString(testHttpAddress))
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val content = response.content ?: fail("No response content")
                val info = objectMapper.readValue<HashMap<String,String>>(content)
                assertNotNull(info["status"])
                assertEquals("ok", info["status"])

                assertEquals(testHttpAddress, Registry.users.toMap()[testUserName])
            }
        }
    }

    @Test
    fun `update new user with illegal name`() {
        withTestApplication({ testModule() }) {
            handleRequest {
                method = HttpMethod.Put
                uri = "/v1/users/${illegalTestUserName}"
                addHeader("Content-type", "application/json")
                setBody(objectMapper.writeValueAsString(testHttpAddress))
            }.apply {
                assertEquals(HttpStatusCode.BadRequest, response.status())
                val content = response.content ?: fail("No response content")
                assertEquals("Illegal user name", content)

                assertFalse(illegalTestUserName in Registry.users.toMap())
            }
        }
    }

    @Test
    fun `list users`() {
        withRegisteredTestUser {
            handleRequest(HttpMethod.Get, "/v1/users").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val content = response.content ?: fail("No response content")
                val users = objectMapper.readValue<HashMap<String, UserAddress>>(content)
                assertEquals(Registry.users.toMap(), users.toMap())
            }
        }
    }

    @Test
    fun `delete user`() {
        withRegisteredTestUser {
            handleRequest {
                method = HttpMethod.Delete
                uri = "/v1/users/${testUserName}"
                addHeader("Content-type", "application/json")
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val content = response.content ?: fail("No response content")
                val info = objectMapper.readValue<HashMap<String,String>>(content)
                assertNotNull(info["status"])
                assertEquals("ok", info["status"])

                assertFalse(testUserName in Registry.users.toMap())
            }
        }
    }

    private fun withRegisteredTestUser(block: TestApplicationEngine.() -> Unit) {
        withTestApplication({ testModule() }) {
            handleRequest {
                method = HttpMethod.Post
                uri = "/v1/users"
                addHeader("Content-type", "application/json")
                setBody(objectMapper.writeValueAsString(userData))
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val content = response.content ?: fail("No response content")
                val info = objectMapper.readValue<HashMap<String,String>>(content)
                assertNotNull(info["status"])
                assertEquals("ok", info["status"])

                this@withTestApplication.block()
            }
        }
    }
}

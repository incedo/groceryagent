package com.groceryautomate.picnic

import com.groceryautomate.picnic.adapter.out.memory.InMemoryPicnicAuthStore
import com.groceryautomate.picnic.domain.PicnicApiException
import com.groceryautomate.picnic.domain.PicnicClientConfig
import com.groceryautomate.picnic.domain.PicnicRouteGeneration
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthenticationContractTest {
    @Test
    fun loginHashesPasswordAndSecondFactorRotatesAuthKey() = runTest {
        val transport = RecordingTransport { request ->
            when {
                request.url.endsWith("/user/login") -> jsonResponse(
                    """{"user_id":"user-1","second_factor_authentication_required":true,"show_second_factor_authentication_intro":false}""",
                    headers = mapOf("X-Picnic-Auth" to listOf("first-token"))
                )
                request.url.endsWith("/user/2fa/verify") -> jsonResponse(
                    "",
                    status = 204,
                    headers = mapOf("x-picnic-auth" to listOf("verified-token"))
                )
                else -> jsonResponse("", status = 204)
            }
        }
        val client = client(transport)

        val login = client.auth.login("person@example.test", "pässword")
        client.auth.generateSecondFactorCode()
        client.auth.verifySecondFactorCode("123456")

        assertTrue(login.secondFactorAuthenticationRequired)
        assertEquals("verified-token", client.auth.currentAuthKey())
        assertEquals(
            """{"client_id":"30100","client_version":"1.239.3","device_id":"3C417201548B2E3B","device_name":"kotlin-multiplatform-client","key":"person@example.test","secret":"8e1843033a0f6ee52e2f618aa8ebbef4"}""",
            transport.requests.first().body?.decodeToString()
        )
        assertTrue(transport.requests.first().url.startsWith("https://gateway.example.test"))
        assertFalse("x-picnic-agent" in transport.requests.first().headers)
        assertFalse("x-picnic-auth" in transport.requests.first().headers)
        assertEquals("first-token", transport.requests[1].headers["x-picnic-auth"])
    }

    @Test
    fun providerErrorsExposeMessageWithoutLeakingBody() = runTest {
        val transport = RecordingTransport {
            jsonResponse(
                """{"error":{"message":"invalid credentials"},"secret":"hidden"}""",
                status = 401
            )
        }

        val failure = runCatching {
            client(transport).auth.login("person", "secret")
        }.exceptionOrNull() as PicnicApiException

        assertEquals(401, failure.statusCode)
        assertEquals("invalid credentials", failure.providerMessage)
        assertEquals("Picnic returned HTTP 401: invalid credentials", failure.message)
        assertFalse(failure.message.orEmpty().contains("hidden"))
    }

    @Test
    fun unsupportedGatewayLoginFallsBackToLegacyStorefrontOnce() = runTest {
        val transport = RecordingTransport { request ->
            if (request.url.startsWith("https://gateway.example.test")) {
                jsonResponse("""{"error":{"code":"NOT_FOUND","message":"Not Found"}}""", 404)
            } else {
                jsonResponse(
                    """{"user_id":"legacy-user","second_factor_authentication_required":false,"show_second_factor_authentication_intro":false}""",
                    headers = mapOf("x-picnic-auth" to listOf("legacy-token"))
                )
            }
        }

        val result = client(transport).auth.login("person", "password")

        assertEquals("legacy-user", result.userId)
        assertEquals(PicnicRouteGeneration.LEGACY, result.routeGeneration)
        assertEquals(2, transport.requests.size)
        assertTrue(transport.requests[0].url.startsWith("https://gateway.example.test"))
        assertTrue(transport.requests[1].url.startsWith("https://storefront.example.test"))
        assertEquals(transport.requests[0].body?.decodeToString(), transport.requests[1].body?.decodeToString())
    }

    @Test
    fun rejectedCredentialsNeverTriggerLegacyLogin() = runTest {
        val transport = RecordingTransport {
            jsonResponse("""{"error":{"code":"UNAUTHORIZED","message":"invalid credentials"}}""", 401)
        }

        assertFailsWith<PicnicApiException> {
            client(transport).auth.login("person", "wrong")
        }

        assertEquals(1, transport.requests.size)
    }

    private fun client(transport: RecordingTransport) = PicnicClient(
        config = PicnicClientConfig(
            gatewayApiBaseUrlOverride = "https://gateway.example.test/api/15",
            storefrontApiBaseUrlOverride = "https://storefront.example.test/api/15"
        ),
        transport = transport,
        authStore = InMemoryPicnicAuthStore()
    )
}

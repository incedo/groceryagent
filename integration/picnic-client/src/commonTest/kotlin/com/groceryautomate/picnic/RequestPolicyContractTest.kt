package com.groceryautomate.picnic

import com.groceryautomate.picnic.adapter.out.memory.InMemoryPicnicAuthStore
import com.groceryautomate.picnic.domain.PicnicClientConfig
import com.groceryautomate.picnic.domain.PicnicCountry
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RequestPolicyContractTest {
    @Test
    fun countryCodesAreNormalizedAndUnsupportedCountriesFailFast() {
        assertEquals(PicnicCountry.NETHERLANDS, PicnicCountry.fromApiCode(" NL "))
        assertEquals(PicnicCountry.FRANCE, PicnicCountry.fromApiCode("fr"))
        assertEquals(
            "https://storefront-prod.fr.picnicinternational.com/api/15",
            PicnicClientConfig(country = PicnicCountry.FRANCE).storefrontApiBaseUrl
        )
        assertFailsWith<IllegalStateException> { PicnicCountry.fromApiCode("es") }
    }

    @Test
    fun authenticatedStorefrontRequestGetsSessionAndDeviceHeadersAutomatically() = runTest {
        val transport = RecordingTransport()
        val client = PicnicClient(
            PicnicClientConfig(
                country = PicnicCountry.GERMANY,
                deviceId = "device-1",
                clientVersion = "1.239.3",
                buildNumber = 15578
            ),
            transport,
            InMemoryPicnicAuthStore("session-1")
        )

        client.app.getBootstrapData()

        val request = transport.requests.single()
        assertTrue(request.url.startsWith("https://storefront-prod.de.picnicinternational.com/api/15"))
        assertEquals("de", request.headers["Accept-Language"])
        assertEquals("session-1", request.headers["x-picnic-auth"])
        assertEquals("device-1", request.headers["x-picnic-did"])
        assertEquals("30100;1.239.3-15578", request.headers["x-picnic-agent"])
        assertFalse("Content-Type" in request.headers)
    }

    @Test
    fun publicGatewayAndAssetsNeverReceiveSessionHeaders() = runTest {
        val transport = RecordingTransport()
        val client = PicnicClient(
            PicnicClientConfig(),
            transport,
            InMemoryPicnicAuthStore("must-not-leak")
        )

        client.customerService.getUnauthenticatedContactInfo("NL")
        client.catalog.getImage("image-1", com.groceryautomate.picnic.domain.PicnicImageSize.SMALL)

        val publicRequest = transport.requests[0]
        assertTrue(publicRequest.url.startsWith("https://gateway-prod.global.picnicinternational.com/public-api/15"))
        assertEquals("NL", publicRequest.headers["picnic-country"])
        assertFalse("x-picnic-auth" in publicRequest.headers)
        assertFalse("x-picnic-did" in publicRequest.headers)
        val assetRequest = transport.requests[1]
        assertTrue(assetRequest.url.startsWith("https://storefront-prod.nl.picnicinternational.com/static/images"))
        assertFalse("x-picnic-auth" in assetRequest.headers)
        assertFalse("x-picnic-did" in assetRequest.headers)
    }

    @Test
    fun currentUpdateAndPushContractsUseCapturedStorefrontMetadata() = runTest {
        val transport = RecordingTransport()
        val client = PicnicClient(
            PicnicClientConfig(
                gatewayApiBaseUrlOverride = "https://gateway.example/api/15",
                storefrontApiBaseUrlOverride = "https://storefront.example/api/15",
                deviceId = "device-test",
                deviceName = "device-name",
                deviceOs = "device-os"
            ),
            transport,
            InMemoryPicnicAuthStore("session")
        )

        client.user.checkForUpdates()
        client.user.registerPushToken("fixture-push-destination")

        assertTrue(transport.requests.all { it.url.startsWith("https://storefront.example/api/15") })
        assertTrue(transport.requests.all { it.headers["x-picnic-auth"] == "session" })
        val update = Json.parseToJsonElement(transport.requests[0].body!!.decodeToString()).jsonObject
        assertEquals("30100", update.getValue("client_id").jsonPrimitive.content)
        assertEquals(15578, update.getValue("build_number").jsonPrimitive.int)
        assertTrue(update.getValue("first_time").jsonPrimitive.boolean)
        assertEquals("device-os", update.getValue("device_os").jsonPrimitive.content)
        assertEquals(false, update.getValue("tracking").jsonObject.getValue("tracking_enabled").jsonPrimitive.boolean)
        val push = Json.parseToJsonElement(transport.requests[1].body!!.decodeToString()).jsonObject
        assertEquals("fixture-push-destination", push.getValue("push_destination").jsonPrimitive.content)
        assertEquals(1, push.getValue("push_version").jsonPrimitive.int)
    }

    @Test
    fun destinationOverridesNormalizeTrailingSlashesIndependently() = runTest {
        val transport = RecordingTransport { request ->
            if (request.url.contains("gateway")) {
                jsonResponse(
                    """{"user_id":"user","second_factor_authentication_required":false,"show_second_factor_authentication_intro":false}""",
                    headers = mapOf("x-picnic-auth" to listOf("token"))
                )
            } else {
                jsonResponse("{}")
            }
        }
        val config = PicnicClientConfig(
            gatewayApiBaseUrlOverride = "https://gateway.example/api/15///",
            storefrontApiBaseUrlOverride = "https://storefront.example/api/15/",
            publicGatewayApiBaseUrlOverride = "https://public.example/public-api/15/",
            storefrontAssetsBaseUrlOverride = "https://assets.example/"
        )
        val client = PicnicClient(config, transport)

        client.auth.login("person", "password")
        client.app.getBootstrapData()
        client.customerService.getUnauthenticatedContactInfo("FR")
        client.catalog.getImage("image", com.groceryautomate.picnic.domain.PicnicImageSize.SMALL)

        assertEquals("https://gateway.example/api/15/user/login", transport.requests[0].url)
        assertEquals("https://storefront.example/api/15/bootstrap", transport.requests[1].url)
        assertEquals("https://public.example/public-api/15/cs-contact-info", transport.requests[2].url)
        assertEquals("https://assets.example/static/images/image/small.png", transport.requests[3].url)
    }
}

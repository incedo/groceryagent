package com.groceryautomate.picnic

import com.groceryautomate.picnic.adapter.out.memory.InMemoryPicnicAuthStore
import com.groceryautomate.picnic.application.port.out.PicnicClock
import com.groceryautomate.picnic.application.port.out.PicnicIdGenerator
import com.groceryautomate.picnic.domain.PicnicApiException
import com.groceryautomate.picnic.domain.PicnicClientConfig
import com.groceryautomate.picnic.domain.PicnicCompatibilityException
import com.groceryautomate.picnic.domain.PicnicFailureReason
import com.groceryautomate.picnic.domain.PicnicRouteGeneration
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CurrentFirstCompatibilityTest {
    @Test
    fun currentProductRouteWinsWithoutCallingLegacy() = runTest {
        val transport = RecordingTransport { currentProductResponse() }

        val details = client(transport).catalog.getProductDetails("s1001")

        assertEquals("Current oats", details.product.name)
        assertEquals(PicnicRouteGeneration.CURRENT, details.source.routeGeneration)
        assertEquals(1, transport.requests.size)
        assertTrue(transport.requests.single().url.contains("/pages/product-details-page-root"))
    }

    @Test
    fun missingCurrentProductRouteFallsBackAndMapsLegacyObject() = runTest {
        val transport = RecordingTransport { request ->
            if (request.url.contains("/pages/product-details-page-root")) {
                jsonResponse(error("NOT_FOUND", "Not Found"), 404)
            } else {
                legacyProductResponse()
            }
        }

        val details = client(transport).catalog.getProductDetails("s1001")

        assertEquals(listOf("/pages/product-details-page-root", "/product/s1001"), paths(transport))
        assertEquals(PicnicRouteGeneration.LEGACY, details.source.routeGeneration)
        assertEquals("Legacy oats", details.product.name)
        assertEquals(249, details.product.priceCents)
        assertEquals("500 g", details.product.unitQuantity)
        assertEquals("Oats", details.ingredients)
        assertEquals(listOf("Gluten"), details.allergens.contains)
        assertEquals(370, details.nutrition?.energyKiloCalories)
    }

    @Test
    fun incompatibleCurrentReadMayFallBackToLegacyMapper() = runTest {
        val transport = RecordingTransport { request ->
            if (request.url.contains("/pages/product-details-page-root")) jsonResponse("{}")
            else legacyProductResponse()
        }

        val details = client(transport).catalog.getProductDetails("s1001")

        assertEquals(PicnicRouteGeneration.LEGACY, details.source.routeGeneration)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun serverFailureDoesNotTriggerLegacyRead() = runTest {
        val transport = RecordingTransport { jsonResponse(error("INTERNAL", "Unavailable"), 503) }

        val failure = assertFailsWith<PicnicApiException> {
            client(transport).catalog.getProductDetails("s1001")
        }

        assertEquals(503, failure.statusCode)
        assertEquals("INTERNAL", failure.providerCode)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun bothReadRoutesFailWithSanitizedAttemptMetadata() = runTest {
        val transport = RecordingTransport { request ->
            if (request.url.contains("/pages/product-details-page-root")) {
                jsonResponse(error("NOT_FOUND", "missing current"), 404)
            } else {
                jsonResponse(error("GONE", "missing legacy"), 410)
            }
        }

        val failure = assertFailsWith<PicnicCompatibilityException> {
            client(transport).catalog.getProductDetails("s1001")
        }

        assertEquals(2, failure.attempts.size)
        assertEquals(PicnicRouteGeneration.CURRENT, failure.attempts[0].generation)
        assertEquals(PicnicFailureReason.ROUTE_UNAVAILABLE, failure.attempts[1].reason)
        assertFalse(failure.message.orEmpty().contains("missing current"))
    }

    @Test
    fun removedCurrentSearchFallsBackToLegacySearchModel() = runTest {
        val transport = RecordingTransport { request ->
            if (request.url.contains("/pages/search-page-root-content")) {
                jsonResponse(error("NOT_FOUND", "Not Found"), 404)
            } else {
                jsonResponse("""[{"items":[{"id":"s1001","name":"Legacy oats","price":249}]}]""")
            }
        }

        val result = client(transport).catalog.search("oats")

        assertEquals(PicnicRouteGeneration.LEGACY, result.source.routeGeneration)
        assertEquals(listOf("Legacy oats"), result.products.map { it.name })
        assertTrue(transport.requests.last().url.endsWith("/search?search_term=oats"))
    }

    @Test
    fun cartMutationFailureIsSentExactlyOnce() = runTest {
        val transport = RecordingTransport { jsonResponse(error("NOT_FOUND", "Not Found"), 404) }

        assertFailsWith<PicnicApiException> {
            client(transport).cart.addProductToCart("s1001")
        }

        assertEquals(1, transport.requests.size)
        assertTrue(transport.requests.single().url.endsWith("/cart/add_product"))
    }

    @Test
    fun unavailableStorefrontUpdateCheckFallsBackToGateway() = runTest {
        val transport = RecordingTransport { request ->
            if (request.url.startsWith("https://storefront.example")) {
                jsonResponse(error("NOT_FOUND", "Not Found"), 404)
            } else {
                jsonResponse("""{"update_available":false}""")
            }
        }
        val picnic = PicnicClient(
            PicnicClientConfig(
                storefrontApiBaseUrlOverride = "https://storefront.example/api/15",
                gatewayApiBaseUrlOverride = "https://gateway.example/api/15"
            ),
            transport,
            InMemoryPicnicAuthStore("fixture-token")
        )

        picnic.user.checkForUpdates()

        assertEquals(2, transport.requests.size)
        assertTrue(transport.requests[0].url.startsWith("https://storefront.example"))
        assertTrue(transport.requests[1].url.startsWith("https://gateway.example"))
        assertEquals(transport.requests[0].body?.decodeToString(), transport.requests[1].body?.decodeToString())
    }

    @Test
    fun pushRegistrationFailureIsNeverRetriedOnGateway() = runTest {
        val transport = RecordingTransport { jsonResponse(error("NOT_FOUND", "Not Found"), 404) }
        val picnic = PicnicClient(
            PicnicClientConfig(
                storefrontApiBaseUrlOverride = "https://storefront.example/api/15",
                gatewayApiBaseUrlOverride = "https://gateway.example/api/15"
            ),
            transport,
            InMemoryPicnicAuthStore("fixture-token")
        )

        assertFailsWith<PicnicApiException> {
            picnic.user.registerPushToken("fixture-destination")
        }

        assertEquals(1, transport.requests.size)
        assertTrue(transport.requests.single().url.startsWith("https://storefront.example"))
    }

    private fun client(transport: RecordingTransport) = PicnicClient(
        config = PicnicClientConfig(baseUrlOverride = "https://picnic.example.test/api/15"),
        transport = transport,
        authStore = InMemoryPicnicAuthStore("fixture-token"),
        clock = PicnicClock { "2026-08-02T12:00:00Z" },
        idGenerator = PicnicIdGenerator { "11111111-1111-4111-8111-111111111111" }
    )

    private fun paths(transport: RecordingTransport): List<String> = transport.requests.map { request ->
        request.url.substringAfter("/api/15").substringBefore('?')
    }
}

private fun currentProductResponse() = jsonResponse(
    """{"sellingUnit":{"id":"s1001","name":"Current oats","price":259,"unit_quantity":"500 g"}}"""
)

private fun legacyProductResponse() = jsonResponse(
    """{
      "product_details": {
        "product_id": "s1001",
        "name": "Legacy oats",
        "price": 249,
        "unit_quantity": "500 g",
        "image_ids": ["image-1"],
        "ingredients_blob": "Oats",
        "allergens": {"contains": ["Gluten"], "may_contain": []},
        "nutritional_info_unit": "Per 100 g",
        "nutritional_values": [
          {"name": "kcal", "value": "370 kcal"},
          {"name": "Protein", "value": "13.2 g"}
        ]
      }
    }""".trimIndent()
)

private fun error(code: String, message: String): String =
    """{"error":{"code":"$code","message":"$message"},"secret":"never expose"}"""

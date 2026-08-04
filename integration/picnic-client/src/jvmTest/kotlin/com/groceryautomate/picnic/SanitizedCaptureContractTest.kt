package com.groceryautomate.picnic

import com.groceryautomate.picnic.adapter.out.memory.InMemoryPicnicAuthStore
import com.groceryautomate.picnic.application.port.out.PicnicClock
import com.groceryautomate.picnic.application.port.out.PicnicHttpResponse
import com.groceryautomate.picnic.application.port.out.PicnicIdGenerator
import com.groceryautomate.picnic.domain.PicnicAllergenDataStatus
import com.groceryautomate.picnic.domain.PicnicClientConfig
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SanitizedCaptureContractTest {
    @Test
    fun productionMappersReadSanitizedCurrentAppResponses() = runTest {
        val productFixture = fixture(PRODUCT_FIXTURE)
        val searchFixture = fixture(SEARCH_FIXTURE)
        val transport = RecordingTransport { request ->
            when {
                "search-page-root-content" in request.url -> jsonResponse(searchFixture)
                "product-details-page-root" in request.url -> jsonResponse(productFixture)
                else -> jsonResponse("{}")
            }
        }
        val client = PicnicClient(
            PicnicClientConfig(baseUrlOverride = "https://picnic.example.test/api/15"),
            transport,
            InMemoryPicnicAuthStore("fixture-token"),
            PicnicClock { "2026-08-02T00:00:00Z" },
            idGenerator = PicnicIdGenerator { "11111111-1111-4111-8111-111111111111" }
        )

        val search = client.catalog.search("fixture query")
        val details = client.catalog.getProductDetails("s9000001")

        assertTrue(search.products.size >= 30)
        assertTrue(search.products.all { it.id.startsWith("s9") && it.name.isNotBlank() })
        assertTrue(search.products.any { it.priceCents != null && it.priceCents > 0 })
        assertTrue(details.product.name.isNotBlank())
        assertTrue(details.product.priceCents != null && details.product.priceCents > 0)
        assertNotNull(details.product.imageId)
        assertTrue(details.infoSections.isNotEmpty())
        assertEquals(PicnicAllergenDataStatus.OBSERVED, details.allergens.status)
        assertTrue(details.allergens.contains.isNotEmpty())
        assertNotNull(details.nutrition)
    }

    @Test
    fun distinctProductionProductLayoutsBecomeSafeTypedObjects() = runTest {
        PRODUCT_VARIANTS.forEach { name ->
            val transport = RecordingTransport { jsonResponse(fixture(name)) }
            val client = PicnicClient(
                PicnicClientConfig(baseUrlOverride = "https://picnic.example.test/api/15"),
                transport,
                InMemoryPicnicAuthStore("fixture-token"),
                PicnicClock { "2026-08-02T00:00:00Z" }
            )

            val details = client.catalog.getProductDetails("s9000001")

            assertEquals("s9000001", details.product.id, name)
            assertTrue(details.product.name.isNotBlank(), name)
            assertTrue(details.product.priceCents != null && details.product.priceCents > 0, name)
            assertNotNull(details.product.imageId, name)
            assertEquals(PicnicAllergenDataStatus.OBSERVED, details.allergens.status, name)
            assertEquals(name != NO_NUTRITION_VARIANT, details.nutrition != null, name)
            assertTrue(details.infoSections.isNotEmpty(), name)
            assertTrue(details.similarProducts.isNotEmpty(), name)
            assertTrue(details.similarProducts.all { it.product.id.startsWith("s9") }, name)
            assertEquals("2026-08-02T00:00:00Z", details.source.observedAt, name)
        }
    }

    @Test
    fun committedFixturesContainNoKnownSensitiveKeysOrValues() {
        val productDocuments = (listOf(PRODUCT_FIXTURE) + PRODUCT_VARIANTS).map(::fixture)
        assertEquals(productDocuments.size, productDocuments.toSet().size, "Product fixtures must be unique")

        ALL_FIXTURES.forEach { name ->
            val text = fixture(name)
            val document = defaultPicnicJson().parseToJsonElement(text)

            assertSanitized(document)
            assertFalse(originalProductId.containsMatchIn(text), "$name contains an original product id")
            assertFalse(longHex.containsMatchIn(text), "$name contains an original long identifier")
        }
    }

    private fun fixture(name: String): String = assertNotNull(
        javaClass.getResourceAsStream("/picnic/$name"),
        "Missing fixture $name"
    ).bufferedReader().use { it.readText() }

    private fun assertSanitized(value: JsonElement) {
        when (value) {
            is JsonObject -> value.forEach { (key, child) ->
                val normalized = key.lowercase().filter { it.isLetterOrDigit() || it == '_' }
                assertFalse(
                    sensitiveKeyParts.any(normalized::contains),
                    "Sensitive key remains in fixture: $key"
                )
                assertSanitized(child)
            }
            is JsonArray -> value.forEach(::assertSanitized)
            is JsonPrimitive -> if (value.isString) assertSafeString(value.jsonPrimitive.content)
        }
    }

    private fun assertSafeString(value: String) {
        val allowedSynthetic = value
            .replace("fixture@example.test", "")
            .replace("+31000000000", "")
            .replace("0000 ZZ", "")
        assertFalse(email.containsMatchIn(allowedSynthetic), "Fixture contains an email-like value")
        assertFalse(jwt.containsMatchIn(value), "Fixture contains a JWT-like value")
        assertFalse(phone.containsMatchIn(allowedSynthetic), "Fixture contains a phone-like value")
        assertFalse(postcode.containsMatchIn(allowedSynthetic), "Fixture contains a postcode-like value")
    }
}

private const val PRODUCT_FIXTURE = "product-details-app-1.239.3.sanitized.json"
private const val SEARCH_FIXTURE = "search-app-1.239.3.sanitized.json"
private val PRODUCT_VARIANTS = (1..6).map { index ->
    "product-details-app-1.239.3-variant-${index.toString().padStart(2, '0')}.sanitized.json"
}
private val ALL_FIXTURES = listOf(PRODUCT_FIXTURE, SEARCH_FIXTURE) + PRODUCT_VARIANTS
private const val NO_NUTRITION_VARIANT =
    "product-details-app-1.239.3-variant-04.sanitized.json"
private val sensitiveKeyParts = setOf(
    "address", "auth", "customer", "device", "email", "firstname", "lastname",
    "phone", "postcode", "session", "token", "userid", "user_id"
)
private val originalProductId = Regex("(?<![A-Za-z0-9])s(?!9)\\d+(?![A-Za-z0-9])")
private val longHex = Regex("(?i)[0-9a-f]{40,}")
private val email = Regex("[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}")
private val jwt = Regex("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")
private val phone = Regex("(?<!\\w)\\+?31[\\s-]?\\d(?:[\\s-]?\\d){8}(?!\\w)")
private val postcode = Regex("\\b\\d{4}\\s?[A-Za-z]{2}\\b")

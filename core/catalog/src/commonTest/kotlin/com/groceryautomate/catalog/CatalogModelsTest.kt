package com.groceryautomate.catalog

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class CatalogModelsTest {
    @Test
    fun canonicalProductRoundTripsWithUnknownSafetyAndEvidence() {
        val product = fixtureCatalogProduct()

        val encoded = Json.encodeToString(product)
        val decoded = Json.decodeFromString<CatalogProduct>(encoded)

        assertEquals(product, decoded)
        assertEquals(VerificationStatus.UNKNOWN, decoded.composition?.allergens?.status)
        assertEquals(AvailabilityStatus.UNKNOWN, decoded.offers.single().availability)
        assertFalse(encoded.contains("picnic-auth", ignoreCase = true))
    }

    @Test
    fun invalidMoneyAndIdentityFailFast() {
        assertFailsWith<IllegalArgumentException> { Money(-1, "EUR") }
        assertFailsWith<IllegalArgumentException> { Money(1, "euro") }
        assertFailsWith<IllegalArgumentException> { ProductId(" ") }
    }
}

private fun fixtureCatalogProduct(): CatalogProduct {
    val id = ProductId("picnic:nl:s1001")
    val evidence = ProviderEvidence(
        provider = "picnic",
        externalId = "s1001",
        endpoint = "/pages/product-details-page-root",
        region = "nl",
        observedAt = "2026-08-04T10:00:00Z",
        apiVersion = 15,
        routeGeneration = ProviderRouteGeneration.CURRENT
    )
    return CatalogProduct(
        product = Product(id, "Oats", "Fixture", null, "image", emptyList()),
        composition = ProductComposition(
            ingredients = null,
            allergens = AllergenStatement(emptyList(), emptyList(), VerificationStatus.UNKNOWN),
            nutrition = null,
            preparation = emptyList(),
            storage = null,
            originCountry = null,
            supplier = null,
            additionalInformation = emptyMap()
        ),
        offers = listOf(
            ProductOffer(
                id = ProductOfferId("picnic:nl:s1001:current"),
                productId = id,
                retailerId = RetailerId("picnic"),
                region = "nl",
                price = Money(199, "EUR"),
                packageQuantity = PackageQuantity(DecimalAmount(500, 0), QuantityUnit.GRAM, originalText = "500 g"),
                tierPrices = emptyList(),
                promotion = null,
                availability = AvailabilityStatus.UNKNOWN,
                evidence = evidence
            )
        ),
        evidence = evidence
    )
}

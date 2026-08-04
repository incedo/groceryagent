package com.groceryautomate.backend

import com.groceryautomate.catalog.AllergenStatement
import com.groceryautomate.catalog.AvailabilityStatus
import com.groceryautomate.catalog.CatalogProduct
import com.groceryautomate.catalog.Money
import com.groceryautomate.catalog.Product
import com.groceryautomate.catalog.ProductCatalogPort
import com.groceryautomate.catalog.ProductComposition
import com.groceryautomate.catalog.ProductId
import com.groceryautomate.catalog.ProductOffer
import com.groceryautomate.catalog.ProductOfferId
import com.groceryautomate.catalog.ProductSearchResult
import com.groceryautomate.catalog.ProviderEvidence
import com.groceryautomate.catalog.ProviderRouteGeneration
import com.groceryautomate.catalog.RetailerId
import com.groceryautomate.catalog.VerificationStatus
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CatalogRoutesTest {
    @Test
    fun searchReturnsCanonicalProductsAndForwardsLimit() = testApplication {
        val catalog = FakeCatalog()
        application { catalogModule(catalog) }

        val response = client.get("/api/v1/products?query=wholegrain%20oats&limit=7")
        val result = Json.decodeFromString<ProductSearchResult>(response.bodyAsText())

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("wholegrain oats", catalog.lastQuery)
        assertEquals(7, catalog.lastLimit)
        assertEquals("picnic:nl:s1001", result.products.single().product.id.value)
        assertEquals(199, result.products.single().offers.single().price.minorUnits)
    }

    @Test
    fun productDetailReturnsCanonicalComposition() = testApplication {
        application { catalogModule(FakeCatalog()) }

        val response = client.get("/api/v1/products/picnic:nl:s1001")
        val product = Json.decodeFromString<CatalogProduct>(response.bodyAsText())

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(VerificationStatus.UNKNOWN, product.composition?.allergens?.status)
        assertEquals("2026-08-04T10:00:00Z", product.evidence.observedAt)
    }

    @Test
    fun missingQueryAndInvalidLimitReturnStableValidationErrors() = testApplication {
        application { catalogModule(FakeCatalog()) }

        val missing = client.get("/api/v1/products")
        val invalidLimit = client.get("/api/v1/products?query=oats&limit=101")

        assertEquals(HttpStatusCode.BadRequest, missing.status)
        assertEquals("INVALID_REQUEST", Json.decodeFromString<ApiError>(missing.bodyAsText()).code)
        assertEquals(HttpStatusCode.BadRequest, invalidLimit.status)
        assertEquals("INVALID_REQUEST", Json.decodeFromString<ApiError>(invalidLimit.bodyAsText()).code)
    }

    @Test
    fun missingProductReturnsStableNotFound() = testApplication {
        application { catalogModule(FakeCatalog(product = null)) }

        val response = client.get("/api/v1/products/picnic:nl:s404")
        val error = Json.decodeFromString<ApiError>(response.bodyAsText())

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("PRODUCT_NOT_FOUND", error.code)
    }

    @Test
    fun providerFailureIsRedacted() = testApplication {
        application {
            catalogModule(FakeCatalog(failure = IllegalStateException("provider secret-token leaked")))
        }

        val response = client.get("/api/v1/products?query=oats")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.BadGateway, response.status)
        assertEquals("PROVIDER_UNAVAILABLE", Json.decodeFromString<ApiError>(body).code)
        assertFalse(body.contains("secret-token"))
        assertFalse(body.contains("provider", ignoreCase = true) && body.contains("leaked"))
    }
}

private class FakeCatalog(
    private val product: CatalogProduct? = catalogProduct(),
    private val failure: Throwable? = null
) : ProductCatalogPort {
    var lastQuery: String? = null
    var lastLimit: Int? = null

    override suspend fun search(query: String, limit: Int): ProductSearchResult {
        failure?.let { throw it }
        lastQuery = query
        lastLimit = limit
        return ProductSearchResult(query, 1, listOfNotNull(product))
    }

    override suspend fun getProduct(id: ProductId): CatalogProduct? {
        failure?.let { throw it }
        return product
    }
}

private fun catalogProduct(): CatalogProduct {
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
        product = Product(id, "Wholegrain oats", "Picnic", null, "image-1", emptyList()),
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
                packageQuantity = null,
                tierPrices = emptyList(),
                promotion = null,
                availability = AvailabilityStatus.UNKNOWN,
                evidence = evidence
            )
        ),
        evidence = evidence
    )
}

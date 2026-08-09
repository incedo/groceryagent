package com.groceryautomate.backend

import com.groceryautomate.catalog.CatalogProduct
import com.groceryautomate.catalog.ProductSearchResult
import com.groceryautomate.catalog.VerificationStatus
import com.groceryautomate.events.ProductImportService
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CatalogRoutesTest {
    @Test
    fun searchAndDetailReadPersistedProjection() = testApplication {
        val repository = FakeEventRepository()
        install(repository, FakeProvider())

        val search = client.get("/api/v1/catalog/products?query=wholegrain%20oats&limit=7")
        val result = Json.decodeFromString<ProductSearchResult>(search.bodyAsText())
        val detail = client.get("/api/v1/catalog/products/picnic:nl:s1001")
        val product = Json.decodeFromString<CatalogProduct>(detail.bodyAsText())

        assertEquals(HttpStatusCode.OK, search.status)
        assertEquals("wholegrain oats", repository.lastQuery)
        assertEquals(7, repository.lastLimit)
        assertEquals(199, result.products.single().offers.single().price.minorUnits)
        assertEquals(VerificationStatus.UNKNOWN, product.composition?.allergens?.status)
    }

    @Test
    fun providerDiscoveryIsExplicitAndTransient() = testApplication {
        val provider = FakeProvider()
        install(FakeEventRepository(), provider)

        val response = client.get("/api/v1/retailers/picnic/products?query=oats&limit=3")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("oats", provider.lastQuery)
        assertEquals(3, provider.lastLimit)
    }

    @Test
    fun importRequiresIdempotencyKeyAndAppendsEvents() = testApplication {
        val repository = FakeEventRepository(null)
        install(repository, FakeProvider())

        val missing = client.post("/api/v1/retailers/picnic/products/s1001/imports")
        val accepted = client.post("/api/v1/retailers/picnic/products/s1001/imports") {
            header("Idempotency-Key", "00000000-0000-4000-8000-000000000001")
        }

        assertEquals(HttpStatusCode.BadRequest, missing.status)
        assertEquals(HttpStatusCode.Accepted, accepted.status)
        assertEquals(2, repository.appended?.events?.size)
    }

    @Test
    fun validationAndMissingProjectionUseStableErrors() = testApplication {
        install(FakeEventRepository(null), FakeProvider())

        val missingQuery = client.get("/api/v1/catalog/products")
        val badLimit = client.get("/api/v1/catalog/products?query=oats&limit=101")
        val missingProduct = client.get("/api/v1/catalog/products/picnic:nl:missing")

        assertEquals(HttpStatusCode.BadRequest, missingQuery.status)
        assertEquals("INVALID_REQUEST", errorCode(missingQuery.bodyAsText()))
        assertEquals(HttpStatusCode.BadRequest, badLimit.status)
        assertEquals(HttpStatusCode.NotFound, missingProduct.status)
        assertEquals("PRODUCT_NOT_FOUND", errorCode(missingProduct.bodyAsText()))
    }

    @Test
    fun providerFailureIsUnavailableAndRedacted() = testApplication {
        val secret = "provider secret-token leaked"
        install(FakeEventRepository(), FakeProvider(failure = IllegalStateException(secret)))

        val response = client.get("/api/v1/retailers/picnic/products?query=oats")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("PROVIDER_UNAVAILABLE", errorCode(body))
        assertFalse(body.contains(secret))
    }

    @Test
    fun legacyCatalogAndProviderRoutesAreAbsent() = testApplication {
        install(FakeEventRepository(), FakeProvider())

        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/products?query=oats").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/products/picnic:nl:s1001").status)
        assertEquals(HttpStatusCode.NotFound, client.post("/api/v1/products/s1001/imports").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/provider-products?query=oats").status)
    }

    @Test
    fun healthSeparatesLivenessFromReadiness() = testApplication {
        install(FakeEventRepository(), FakeProvider(), ready = false)

        assertEquals(HttpStatusCode.OK, client.get("/health/live").status)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/health/ready").status)
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.install(
        repository: FakeEventRepository,
        provider: FakeProvider,
        ready: Boolean = true
    ) {
        val ids = ArrayDeque(
            listOf(
                "00000000-0000-4000-8000-000000000002",
                "00000000-0000-4000-8000-000000000003"
            )
        )
        val gateway = ProviderCatalogGateway(provider)
        application {
            catalogModule(
                repository,
                gateway,
                ProductImportService(gateway, repository, { ids.removeFirst() }) { "2026-08-04T11:00:00Z" },
                readiness = { ready }
            )
        }
    }

    private fun errorCode(body: String): String = Json.decodeFromString<ApiError>(body).code
}

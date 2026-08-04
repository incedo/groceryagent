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
import com.groceryautomate.events.AppendCatalogEvents
import com.groceryautomate.events.AppendResult
import com.groceryautomate.events.CatalogEventRepository
import com.groceryautomate.events.CommandId
import com.groceryautomate.events.EventPage
import com.groceryautomate.events.StreamId

internal val testProduct: CatalogProduct by lazy {
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
    CatalogProduct(
        product = Product(id, "Wholegrain oats", "Picnic", null, "image-1"),
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
                ProductOfferId("picnic:nl:s1001:current"), id, RetailerId("picnic"), "nl",
                Money(199, "EUR"), null, emptyList(), null, AvailabilityStatus.UNKNOWN, evidence
            )
        ),
        evidence = evidence
    )
}

internal class FakeProvider(
    private val product: CatalogProduct? = testProduct,
    private val failure: Throwable? = null
) : ProductCatalogPort {
    var lastQuery: String? = null
    var lastLimit: Int? = null
    var getCalls: Int = 0

    override suspend fun search(query: String, limit: Int): ProductSearchResult {
        failure?.let { throw it }
        lastQuery = query
        lastLimit = limit
        return ProductSearchResult(query, 1, listOfNotNull(product))
    }

    override suspend fun getProduct(id: ProductId): CatalogProduct? {
        getCalls++
        failure?.let { throw it }
        return product
    }
}

internal class FakeEventRepository(
    private var product: CatalogProduct? = testProduct
) : CatalogEventRepository {
    var lastQuery: String? = null
    var lastLimit: Int? = null
    var appended: AppendCatalogEvents? = null

    override suspend fun findCommand(commandId: CommandId): AppendResult? = null
    override suspend fun streamVersion(streamId: StreamId): Long = 0

    override suspend fun append(request: AppendCatalogEvents): AppendResult {
        appended = request
        product = testProduct
        return AppendResult(request.streamId, request.events.size.toLong(), 1, request.events.size.toLong(), request.events.size, false)
    }

    override suspend fun search(query: String, limit: Int): ProductSearchResult {
        lastQuery = query
        lastLimit = limit
        return ProductSearchResult(query, if (product == null) 0 else 1, listOfNotNull(product))
    }

    override suspend fun getProduct(id: ProductId): CatalogProduct? = product
    override suspend fun readEvents(after: Long, limit: Int): EventPage = EventPage(after, after, emptyList())
    override suspend fun rebuildProjections(): Int = 0
}

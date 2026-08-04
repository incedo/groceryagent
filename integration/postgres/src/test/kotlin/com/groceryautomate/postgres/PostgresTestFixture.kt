package com.groceryautomate.postgres

import com.groceryautomate.catalog.AvailabilityStatus
import com.groceryautomate.catalog.CatalogProduct
import com.groceryautomate.catalog.Money
import com.groceryautomate.catalog.Product
import com.groceryautomate.catalog.ProductId
import com.groceryautomate.catalog.ProductOffer
import com.groceryautomate.catalog.ProductOfferId
import com.groceryautomate.catalog.ProviderEvidence
import com.groceryautomate.catalog.ProviderRouteGeneration
import com.groceryautomate.catalog.RetailerId
import com.groceryautomate.events.AppendCatalogEvents
import com.groceryautomate.events.CommandId
import com.groceryautomate.events.EventId
import com.groceryautomate.events.OfferObserved
import com.groceryautomate.events.ProducerId
import com.groceryautomate.events.ProductImported
import com.groceryautomate.events.ProposedCatalogEvent
import com.groceryautomate.events.StreamId

internal const val COMMAND_ID = "00000000-0000-4000-8000-000000000001"
internal const val PRODUCT_EVENT_ID = "00000000-0000-4000-8000-000000000002"
internal const val OFFER_EVENT_ID = "00000000-0000-4000-8000-000000000003"
internal const val OCCURRED_AT = "2026-08-04T10:00:00Z"

internal fun catalogAppend(
    commandId: String = COMMAND_ID,
    streamId: String = "product:picnic:nl:s1",
    expectedVersion: Long = 0,
    events: List<ProposedCatalogEvent> = catalogEvents()
): AppendCatalogEvents = AppendCatalogEvents(
    commandId = CommandId(commandId),
    streamId = StreamId(streamId),
    expectedVersion = expectedVersion,
    producerId = ProducerId("component-test"),
    correlationId = CommandId(commandId),
    events = events
)

internal fun catalogEvents(): List<ProposedCatalogEvent> {
    val product = fixtureCatalogProduct()
    return listOf(
        ProposedCatalogEvent(
            EventId(PRODUCT_EVENT_ID),
            OCCURRED_AT,
            ProductImported(product.product, product.composition, product.evidence)
        ),
        ProposedCatalogEvent(
            EventId(OFFER_EVENT_ID),
            OCCURRED_AT,
            OfferObserved(product.offers.single())
        )
    )
}

internal fun fixtureCatalogProduct(): CatalogProduct {
    val productId = ProductId("picnic:nl:s1")
    val evidence = ProviderEvidence(
        provider = "picnic",
        externalId = "s1",
        endpoint = "/pages/product-details-page-root",
        region = "nl",
        observedAt = OCCURRED_AT,
        apiVersion = 15,
        routeGeneration = ProviderRouteGeneration.CURRENT
    )
    return CatalogProduct(
        product = Product(productId, "Wholegrain oats", "Fixture", null, null),
        composition = null,
        offers = listOf(
            ProductOffer(
                id = ProductOfferId("picnic:nl:s1:current"),
                productId = productId,
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

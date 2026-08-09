package com.groceryautomate.events

import com.groceryautomate.catalog.CatalogProduct
import com.groceryautomate.catalog.Product
import com.groceryautomate.catalog.ProductId
import com.groceryautomate.catalog.ProviderEvidence
import com.groceryautomate.catalog.ProviderRouteGeneration
import com.groceryautomate.catalog.HistoricalPriceObservation
import com.groceryautomate.catalog.HistoricalPriceObservationId
import com.groceryautomate.catalog.Money
import com.groceryautomate.catalog.RetailerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CatalogEventContractTest {
    @Test
    fun productImportRoundTripsAndStartsProjection() {
        val event = imported()
        val payload = CatalogEventCodec.encode(event)

        assertEquals(event, CatalogEventCodec.decode(event.eventType, event.schemaVersion, payload))
        assertEquals(event.product, reduceCatalogProduct(null, event).product)
    }

    @Test
    fun unknownSchemaAndInvalidIdsFailClosed() {
        val event = imported()

        assertFailsWith<IllegalArgumentException> {
            CatalogEventCodec.decode(event.eventType, 2, CatalogEventCodec.encode(event))
        }
        assertFailsWith<IllegalArgumentException> { EventId("not-a-uuid") }
    }

    @Test
    fun historicalPriceRoundTripsWithoutRoundingLineTotal() {
        val event = HistoricalPriceObserved(
            HistoricalPriceObservation(
                HistoricalPriceObservationId("history-1"),
                ProductId("picnic:nl:s1"),
                RetailerId("picnic"),
                "nl",
                Money(949, "EUR"),
                Money(999, "EUR"),
                2,
                "500 gram",
                "Bonus",
                "2025-01-02T10:00:00Z",
                "picnic-completed-order"
            )
        )

        assertEquals(event, CatalogEventCodec.decode(
            event.eventType, event.schemaVersion, CatalogEventCodec.encode(event)
        ))
    }
}

private fun imported(): ProductImported = ProductImported(
    product = Product(ProductId("picnic:nl:s1"), "Oats", null, null, null),
    composition = null,
    evidence = ProviderEvidence(
        provider = "picnic",
        externalId = "s1",
        endpoint = "/pages/product-details-page-root",
        region = "nl",
        observedAt = "2026-08-04T10:00:00Z",
        apiVersion = 15,
        routeGeneration = ProviderRouteGeneration.CURRENT
    )
)

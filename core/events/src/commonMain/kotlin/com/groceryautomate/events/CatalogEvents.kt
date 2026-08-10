package com.groceryautomate.events

import com.groceryautomate.catalog.Product
import com.groceryautomate.catalog.ProductComposition
import com.groceryautomate.catalog.ProductOffer
import com.groceryautomate.catalog.ProductId
import com.groceryautomate.catalog.ProviderEvidence
import com.groceryautomate.catalog.HistoricalPriceObservation
import kotlinx.serialization.Serializable

const val PRODUCT_IMPORTED_TYPE = "ProductImported"
const val OFFER_OBSERVED_TYPE = "OfferObserved"
const val HISTORICAL_PRICE_OBSERVED_TYPE = "HistoricalPriceObserved"
const val PREVIOUS_PRODUCT_ID_LINKED_TYPE = "PreviousProductIdLinked"
const val CATALOG_EVENT_SCHEMA_VERSION = 1

sealed interface CatalogEvent {
    val eventType: String
    val schemaVersion: Int
}

@Serializable
data class ProductImported(
    val product: Product,
    val composition: ProductComposition?,
    val evidence: ProviderEvidence
) : CatalogEvent {
    override val eventType: String = PRODUCT_IMPORTED_TYPE
    override val schemaVersion: Int = CATALOG_EVENT_SCHEMA_VERSION
}

@Serializable
data class OfferObserved(
    val offer: ProductOffer
) : CatalogEvent {
    override val eventType: String = OFFER_OBSERVED_TYPE
    override val schemaVersion: Int = CATALOG_EVENT_SCHEMA_VERSION
}

@Serializable
data class HistoricalPriceObserved(
    val observation: HistoricalPriceObservation
) : CatalogEvent {
    override val eventType: String = HISTORICAL_PRICE_OBSERVED_TYPE
    override val schemaVersion: Int = CATALOG_EVENT_SCHEMA_VERSION
}

@Serializable
data class PreviousProductIdLinked(
    val productId: ProductId,
    val previousProductId: ProductId,
    val matchedName: String,
    val matchedUnitQuantity: String,
    val evidence: ProviderEvidence
) : CatalogEvent {
    init {
        require(productId != previousProductId) { "Previous product id must differ from current id." }
        require(matchedName.isNotBlank()) { "Matched product name must not be blank." }
        require(matchedUnitQuantity.isNotBlank()) { "Matched unit quantity must not be blank." }
    }

    override val eventType: String = PREVIOUS_PRODUCT_ID_LINKED_TYPE
    override val schemaVersion: Int = CATALOG_EVENT_SCHEMA_VERSION
}

package com.groceryautomate.events

import com.groceryautomate.catalog.Product
import com.groceryautomate.catalog.ProductComposition
import com.groceryautomate.catalog.ProductOffer
import com.groceryautomate.catalog.ProviderEvidence
import kotlinx.serialization.Serializable

const val PRODUCT_IMPORTED_TYPE = "ProductImported"
const val OFFER_OBSERVED_TYPE = "OfferObserved"
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

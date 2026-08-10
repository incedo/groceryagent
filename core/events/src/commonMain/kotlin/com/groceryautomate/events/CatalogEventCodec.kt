package com.groceryautomate.events

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

object CatalogEventCodec {
    val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
    }

    fun encode(event: CatalogEvent): JsonObject = when (event) {
        is ProductImported -> json.encodeToJsonElement(event)
        is OfferObserved -> json.encodeToJsonElement(event)
        is HistoricalPriceObserved -> json.encodeToJsonElement(event)
        is PreviousProductIdLinked -> json.encodeToJsonElement(event)
        is ProductImageStored -> json.encodeToJsonElement(event)
    } as JsonObject

    fun decode(type: String, schemaVersion: Int, payload: JsonObject): CatalogEvent {
        require(schemaVersion == CATALOG_EVENT_SCHEMA_VERSION) {
            "Unsupported catalog event schema: $type v$schemaVersion."
        }
        return when (type) {
            PRODUCT_IMPORTED_TYPE -> json.decodeFromJsonElement<ProductImported>(payload)
            OFFER_OBSERVED_TYPE -> json.decodeFromJsonElement<OfferObserved>(payload)
            HISTORICAL_PRICE_OBSERVED_TYPE -> json.decodeFromJsonElement<HistoricalPriceObserved>(payload)
            PREVIOUS_PRODUCT_ID_LINKED_TYPE -> json.decodeFromJsonElement<PreviousProductIdLinked>(payload)
            PRODUCT_IMAGE_STORED_TYPE -> json.decodeFromJsonElement<ProductImageStored>(payload)
            else -> error("Unsupported catalog event type: $type.")
        }
    }
}

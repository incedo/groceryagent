package com.groceryautomate.postgres

import com.groceryautomate.catalog.CatalogProduct
import com.groceryautomate.events.CatalogEventCodec
import com.groceryautomate.events.CatalogEvent
import com.groceryautomate.events.EventEnvelope
import com.groceryautomate.events.HistoricalPriceObserved
import com.groceryautomate.events.PreviousProductIdLinked
import com.groceryautomate.events.ProductImageStored
import com.groceryautomate.events.reduceCatalogProduct
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.sql.Connection

internal class PostgresProjectionWriter(
    private val connection: Connection
) {
    fun apply(envelope: EventEnvelope) {
        val event = CatalogEventCodec.decode(
            envelope.eventType,
            envelope.schemaVersion,
            envelope.payload
        )
        if (event is HistoricalPriceObserved) {
            applyHistoricalPrice(envelope, event)
        } else if (event is ProductImageStored) {
            applyProductImage(envelope, event)
        } else {
            applyCatalog(envelope, event)
        }
    }

    private fun applyProductImage(envelope: EventEnvelope, event: ProductImageStored) {
        val asset = event.asset
        require(envelope.streamId.value == "product:${asset.productId.value}") {
            "Product image asset belongs to another stream."
        }
        val document = CatalogEventCodec.json.encodeToString(asset)
        connection.prepareStatement(
            """
            INSERT INTO product_image_assets(
                product_id, variant, source_image_id, document, last_global_position
            ) VALUES (?, ?, ?, ?::jsonb, ?)
            ON CONFLICT (product_id, variant) DO UPDATE SET
                source_image_id = EXCLUDED.source_image_id,
                document = EXCLUDED.document,
                last_global_position = EXCLUDED.last_global_position
            WHERE product_image_assets.last_global_position < EXCLUDED.last_global_position
            """.trimIndent()
        ).use {
            it.setString(1, asset.productId.value)
            it.setString(2, asset.variant.name)
            it.setString(3, asset.sourceImageId)
            it.setString(4, document)
            it.setLong(5, envelope.globalPosition)
            it.executeUpdate()
        }
    }

    private fun applyCatalog(envelope: EventEnvelope, event: CatalogEvent) {
        val existing = load(envelope.streamId.value)
        if (existing != null && envelope.globalPosition <= existing.lastGlobalPosition) return
        val product = reduceCatalogProduct(existing?.product, event)
        val document = CatalogEventCodec.json.encodeToString(product)
        connection.prepareStatement(
            """
            INSERT INTO catalog_products(product_id, name, brand, document, last_global_position)
            VALUES (?, ?, ?, ?::jsonb, ?)
            ON CONFLICT (product_id) DO UPDATE SET
                name = EXCLUDED.name,
                brand = EXCLUDED.brand,
                document = EXCLUDED.document,
                last_global_position = EXCLUDED.last_global_position
            """.trimIndent()
        ).use {
            it.setString(1, product.product.id.value)
            it.setString(2, product.product.name)
            it.setString(3, product.product.brand)
            it.setString(4, document)
            it.setLong(5, envelope.globalPosition)
            it.executeUpdate()
        }
        if (event is PreviousProductIdLinked) applyPreviousId(envelope, event)
    }

    private fun applyPreviousId(envelope: EventEnvelope, event: PreviousProductIdLinked) {
        connection.prepareStatement(
            """
            INSERT INTO product_previous_ids(previous_product_id, product_id, last_global_position)
            VALUES (?, ?, ?) ON CONFLICT (previous_product_id) DO NOTHING
            """.trimIndent()
        ).use {
            it.setString(1, event.previousProductId.value)
            it.setString(2, event.productId.value)
            it.setLong(3, envelope.globalPosition)
            it.executeUpdate()
        }
        val linkedProductId = connection.prepareStatement(
            "SELECT product_id FROM product_previous_ids WHERE previous_product_id = ?"
        ).use {
            it.setString(1, event.previousProductId.value)
            it.executeQuery().use { result ->
                check(result.next()) { "Previous product id projection was not written." }
                result.getString(1)
            }
        }
        require(linkedProductId == event.productId.value) {
            "Previous product id is already linked to another product."
        }
    }

    private fun applyHistoricalPrice(envelope: EventEnvelope, event: HistoricalPriceObserved) {
        val observation = event.observation
        val document = CatalogEventCodec.json.encodeToString(observation)
        connection.prepareStatement(
            """
            INSERT INTO product_price_history(
                observation_id, product_id, retailer_id, purchased_at, document, last_global_position
            ) VALUES (?, ?, ?, ?::timestamptz, ?::jsonb, ?)
            ON CONFLICT (observation_id) DO NOTHING
            """.trimIndent()
        ).use {
            it.setString(1, observation.id.value)
            it.setString(2, observation.productId.value)
            it.setString(3, observation.retailerId.value)
            it.setString(4, observation.purchasedAt)
            it.setString(5, document)
            it.setLong(6, envelope.globalPosition)
            it.executeUpdate()
        }
    }

    private fun load(streamId: String): ProjectionState? {
        val productId = streamId.removePrefix("product:")
        return connection.prepareStatement(
            "SELECT document, last_global_position FROM catalog_products WHERE product_id = ? FOR UPDATE"
        ).use {
            it.setString(1, productId)
            it.executeQuery().use { result ->
                if (!result.next()) null else ProjectionState(
                    CatalogEventCodec.json.decodeFromString(result.getString("document")),
                    result.getLong("last_global_position")
                )
            }
        }
    }
}

private data class ProjectionState(
    val product: CatalogProduct,
    val lastGlobalPosition: Long
)

package com.groceryautomate.postgres

import com.groceryautomate.catalog.CatalogProduct
import com.groceryautomate.events.CatalogEventCodec
import com.groceryautomate.events.EventEnvelope
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

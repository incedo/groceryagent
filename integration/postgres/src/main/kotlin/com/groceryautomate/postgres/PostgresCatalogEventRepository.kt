package com.groceryautomate.postgres

import com.groceryautomate.catalog.CatalogProduct
import com.groceryautomate.catalog.ProductId
import com.groceryautomate.catalog.ProductSearchResult
import com.groceryautomate.catalog.ProductPriceHistory
import com.groceryautomate.catalog.HistoricalPriceObservation
import com.groceryautomate.catalog.ProductImageAsset
import com.groceryautomate.catalog.ProductImageAssetPort
import com.groceryautomate.catalog.ProductImageImportCandidate
import com.groceryautomate.catalog.ProductImageVariant
import com.groceryautomate.events.AppendCatalogEvents
import com.groceryautomate.events.AppendResult
import com.groceryautomate.events.CatalogEventCodec
import com.groceryautomate.events.CatalogEventRepository
import com.groceryautomate.events.CommandId
import com.groceryautomate.events.EventPage
import com.groceryautomate.events.StreamId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import java.sql.Connection
import javax.sql.DataSource

class PostgresCatalogEventRepository(
    private val dataSource: DataSource
) : CatalogEventRepository, ProductImageAssetPort {
    override suspend fun findCommand(commandId: CommandId): AppendResult? = io {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT stream_id, stream_version, first_global_position, last_global_position, event_count
                FROM event_commands WHERE command_id = ? AND event_count IS NOT NULL
                """.trimIndent()
            ).use {
                it.setObject(1, java.util.UUID.fromString(commandId.value))
                it.executeQuery().use { result ->
                    if (!result.next()) null else AppendResult(
                        StreamId(result.getString("stream_id")),
                        result.getLong("stream_version"),
                        result.getLong("first_global_position"),
                        result.getLong("last_global_position"),
                        result.getInt("event_count"),
                        duplicateCommand = true
                    )
                }
            }
        }
    }

    override suspend fun streamVersion(streamId: StreamId): Long = io {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT current_version FROM event_streams WHERE stream_id = ?"
            ).use {
                it.setString(1, streamId.value)
                it.executeQuery().use { result -> if (result.next()) result.getLong(1) else 0L }
            }
        }
    }

    override suspend fun append(request: AppendCatalogEvents): AppendResult = io {
        dataSource.connection.use { connection ->
            connection.inTransaction { PostgresEventAppender(this).append(request) }
        }
    }

    override suspend fun search(query: String, limit: Int): ProductSearchResult = io {
        require(query.isNotBlank()) { "Catalog query must not be blank." }
        require(limit in 1..100) { "Catalog limit must be between 1 and 100." }
        dataSource.connection.use { connection ->
            val total = connection.prepareStatement(
                "SELECT count(*) FROM catalog_products WHERE strpos(lower(name), lower(?)) > 0"
            ).use {
                it.setString(1, query.trim())
                it.executeQuery().use { result -> result.next(); result.getInt(1) }
            }
            val products = connection.prepareStatement(
                """
                SELECT document FROM catalog_products
                WHERE strpos(lower(name), lower(?)) > 0
                ORDER BY lower(name), product_id LIMIT ?
                """.trimIndent()
            ).use {
                it.setString(1, query.trim())
                it.setInt(2, limit)
                it.executeQuery().use { result ->
                    buildList {
                        while (result.next()) add(decodeProduct(result.getString("document")))
                    }
                }
            }
            ProductSearchResult(query.trim(), total, products)
        }
    }

    override suspend fun getProduct(id: ProductId): CatalogProduct? = io {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT document FROM catalog_products
                WHERE product_id = ? OR product_id = (
                    SELECT product_id FROM product_previous_ids WHERE previous_product_id = ?
                )
                ORDER BY CASE WHEN product_id = ? THEN 0 ELSE 1 END
                LIMIT 1
                """.trimIndent()
            ).use {
                it.setString(1, id.value)
                it.setString(2, id.value)
                it.setString(3, id.value)
                it.executeQuery().use { result ->
                    if (result.next()) decodeProduct(result.getString("document")) else null
                }
            }
        }
    }

    override suspend fun getPriceHistory(productId: ProductId, limit: Int): ProductPriceHistory = io {
        require(limit in 1..1000) { "Price history limit must be between 1 and 1000." }
        val observations = dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT document FROM product_price_history
                WHERE product_id = ? ORDER BY purchased_at, observation_id LIMIT ?
                """.trimIndent()
            ).use {
                it.setString(1, productId.value)
                it.setInt(2, limit)
                it.executeQuery().use { result ->
                    buildList {
                        while (result.next()) add(
                            CatalogEventCodec.json.decodeFromString<HistoricalPriceObservation>(
                                result.getString("document")
                            )
                        )
                    }
                }
            }
        }
        ProductPriceHistory(productId, observations)
    }

    override suspend fun findImageImportCandidates(
        variant: ProductImageVariant,
        limit: Int
    ): List<ProductImageImportCandidate> = io {
        require(limit in 1..50) { "Image import limit must be between 1 and 50." }
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT product.product_id, product.document->'product'->>'imageId' AS source_image_id
                FROM catalog_products product
                WHERE nullif(product.document->'product'->>'imageId', '') IS NOT NULL
                  AND NOT EXISTS (
                    SELECT 1 FROM product_image_assets asset
                    WHERE asset.product_id = product.product_id
                      AND asset.variant = ?
                      AND asset.source_image_id = product.document->'product'->>'imageId'
                  )
                ORDER BY product_id LIMIT ?
                """.trimIndent()
            ).use {
                it.setString(1, variant.name)
                it.setInt(2, limit)
                it.executeQuery().use { result ->
                    buildList {
                        while (result.next()) add(
                            ProductImageImportCandidate(
                                ProductId(result.getString("product_id")),
                                result.getString("source_image_id")
                            )
                        )
                    }
                }
            }
        }
    }

    override suspend fun getProductImageAsset(
        productId: ProductId,
        variant: ProductImageVariant
    ): ProductImageAsset? = io {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT document FROM product_image_assets WHERE product_id = ? AND variant = ?"
            ).use {
                it.setString(1, productId.value)
                it.setString(2, variant.name)
                it.executeQuery().use { result ->
                    if (result.next()) {
                        CatalogEventCodec.json.decodeFromString<ProductImageAsset>(result.getString("document"))
                    } else {
                        null
                    }
                }
            }
        }
    }

    override suspend fun readEvents(after: Long, limit: Int): EventPage = io {
        require(after >= 0) { "Event cursor must not be negative." }
        require(limit in 1..1000) { "Event limit must be between 1 and 1000." }
        dataSource.connection.use { connection ->
            val events = connection.prepareStatement(
                """
                SELECT $eventSelectColumns FROM domain_events
                WHERE global_position > ? ORDER BY global_position LIMIT ?
                """.trimIndent()
            ).use {
                it.setLong(1, after)
                it.setInt(2, limit)
                it.executeQuery().use { result ->
                    buildList { while (result.next()) add(result.toEventEnvelope()) }
                }
            }
            EventPage(after, events.lastOrNull()?.globalPosition ?: after, events)
        }
    }

    override suspend fun rebuildProjections(): Int = io {
        dataSource.connection.use { connection ->
            connection.inTransaction {
                createStatement().use { it.executeUpdate("DELETE FROM product_image_assets") }
                createStatement().use { it.executeUpdate("DELETE FROM product_price_history") }
                createStatement().use { it.executeUpdate("DELETE FROM product_previous_ids") }
                createStatement().use { it.executeUpdate("DELETE FROM catalog_products") }
                val writer = PostgresProjectionWriter(this)
                createStatement().use { statement ->
                    statement.executeQuery(
                        "SELECT $eventSelectColumns FROM domain_events ORDER BY global_position"
                    ).use { result ->
                        var count = 0
                        while (result.next()) {
                            writer.apply(result.toEventEnvelope())
                            count++
                        }
                        count
                    }
                }
            }
        }
    }

    suspend fun isReady(): Boolean = io {
        runCatching {
            dataSource.connection.use { connection ->
                connection.createStatement().use { it.executeQuery("SELECT 1").use { result -> result.next() } }
            }
        }.getOrDefault(false)
    }

    private fun decodeProduct(value: String): CatalogProduct = CatalogEventCodec.json.decodeFromString(value)

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }
}

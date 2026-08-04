package com.groceryautomate.postgres

import com.groceryautomate.catalog.CatalogProduct
import com.groceryautomate.catalog.ProductId
import com.groceryautomate.catalog.ProductSearchResult
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
) : CatalogEventRepository {
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
                "SELECT document FROM catalog_products WHERE product_id = ?"
            ).use {
                it.setString(1, id.value)
                it.executeQuery().use { result ->
                    if (result.next()) decodeProduct(result.getString("document")) else null
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

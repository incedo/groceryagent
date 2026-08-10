package com.groceryautomate.postgres

import com.groceryautomate.catalog.ProductId
import com.groceryautomate.events.CommandConflict
import com.groceryautomate.events.CommandId
import com.groceryautomate.events.EventId
import com.groceryautomate.events.OfferObserved
import com.groceryautomate.events.PreviousProductIdLinked
import com.groceryautomate.events.ProposedCatalogEvent
import com.groceryautomate.events.StreamId
import com.groceryautomate.events.StreamVersionConflict
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeAll
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.containers.wait.strategy.Wait
import java.sql.SQLException
import javax.sql.DataSource
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Testcontainers(disabledWithoutDocker = true)
class PostgresCatalogEventRepositoryTest {
    private lateinit var dataSource: DataSource
    private lateinit var repository: PostgresCatalogEventRepository

    @BeforeTest
    fun resetDatabase() {
        dataSource = PGSimpleDataSource().apply {
            setUrl(postgres.jdbcUrl)
            user = postgres.username
            password = postgres.password
        }
        waitForDatabase()
        dataSource.connection.use { connection ->
            connection.createStatement().use {
                it.execute("DROP SCHEMA public CASCADE")
                it.execute("CREATE SCHEMA public")
            }
        }
        assertEquals(3, PostgresMigrator(dataSource).migrate())
        repository = PostgresCatalogEventRepository(dataSource)
    }

    private fun waitForDatabase() {
        var lastFailure: Throwable? = null
        repeat(100) {
            runCatching { dataSource.connection.use { connection -> connection.isValid(1) } }
                .onSuccess { if (it) return }
                .onFailure { lastFailure = it }
            Thread.sleep(100)
        }
        throw IllegalStateException("PostgreSQL container did not accept connections.", lastFailure)
    }

    @Test
    fun appendProjectsReadsCursorAndDeduplicatesCommand() = runTest {
        val first = repository.append(catalogAppend())
        val duplicate = repository.append(catalogAppend())

        assertEquals(2, first.streamVersion)
        assertEquals(2, first.eventCount)
        assertTrue(duplicate.duplicateCommand)
        assertEquals(first.copy(duplicateCommand = true), duplicate)
        assertEquals(duplicate, repository.findCommand(CommandId(COMMAND_ID)))
        assertEquals(2, repository.readEvents(0, 100).events.size)
        assertEquals(1, repository.readEvents(first.firstGlobalPosition, 100).events.size)
        assertEquals(fixtureCatalogProduct(), repository.getProduct(ProductId("picnic:nl:s1")))
        assertEquals(1, repository.search("OATS", 10).totalProviderCount)
    }

    @Test
    fun enforcesExpectedVersionAndCommandScope() = runTest {
        repository.append(catalogAppend())

        assertFailsWith<StreamVersionConflict> {
            repository.append(
                catalogAppend(
                    commandId = "00000000-0000-4000-8000-000000000004",
                    expectedVersion = 0
                )
            )
        }
        assertFailsWith<CommandConflict> {
            repository.append(catalogAppend(streamId = "product:picnic:nl:other"))
        }
        assertEquals(2, repository.streamVersion(StreamId("product:picnic:nl:s1")))
    }

    @Test
    fun failedProjectionRollsBackEventsCommandAndStream() = runTest {
        val offerFirst = ProposedCatalogEvent(
            EventId(OFFER_EVENT_ID),
            OCCURRED_AT,
            OfferObserved(fixtureCatalogProduct().offers.single())
        )

        assertFailsWith<IllegalArgumentException> {
            repository.append(catalogAppend(events = listOf(offerFirst)))
        }
        assertEquals(0, repository.streamVersion(StreamId("product:picnic:nl:s1")))
        assertTrue(repository.readEvents(0, 10).events.isEmpty())
    }

    @Test
    fun duplicateEventIdOnAnotherCommandRollsBack() = runTest {
        repository.append(catalogAppend())

        assertFailsWith<SQLException> {
            repository.append(
                catalogAppend(
                    commandId = "00000000-0000-4000-8000-000000000005",
                    expectedVersion = 2,
                    events = catalogEvents().take(1)
                )
            )
        }
        assertEquals(2, repository.streamVersion(StreamId("product:picnic:nl:s1")))
    }

    @Test
    fun eventLogRejectsMutationAndDeletion() = runTest {
        repository.append(catalogAppend())

        dataSource.connection.use { connection ->
            assertFailsWith<SQLException> {
                connection.createStatement().use { it.executeUpdate("UPDATE domain_events SET producer_id = 'changed'") }
            }
            assertFailsWith<SQLException> {
                connection.createStatement().use { it.executeUpdate("DELETE FROM domain_events") }
            }
            assertFailsWith<SQLException> {
                connection.createStatement().use { it.executeUpdate("TRUNCATE domain_events") }
            }
        }
        assertEquals(2, repository.readEvents(0, 10).events.size)
    }

    @Test
    fun projectionRebuildIsDeterministic() = runTest {
        repository.append(catalogAppend())
        repository.append(historicalPriceAppend())
        val before = repository.getProduct(ProductId("picnic:nl:s1"))
        val historyBefore = repository.getPriceHistory(ProductId("picnic:nl:s1"))

        assertEquals(3, repository.rebuildProjections())
        assertEquals(before, repository.getProduct(ProductId("picnic:nl:s1")))
        assertEquals(historyBefore, repository.getPriceHistory(ProductId("picnic:nl:s1")))
    }

    @Test
    fun previousProductIdResolvesCurrentProductAndSurvivesRebuild() = runTest {
        repository.append(catalogAppend())
        val previousId = ProductId("picnic:nl:s-previous")
        repository.append(
            catalogAppend(
                commandId = "00000000-0000-4000-8000-000000000010",
                expectedVersion = 2,
                events = listOf(
                    ProposedCatalogEvent(
                        EventId("00000000-0000-4000-8000-000000000011"),
                        OCCURRED_AT,
                        PreviousProductIdLinked(
                            productId = ProductId("picnic:nl:s1"),
                            previousProductId = previousId,
                            matchedName = "Wholegrain oats",
                            matchedUnitQuantity = "500 gram",
                            evidence = fixtureCatalogProduct().evidence
                        )
                    )
                )
            )
        )

        val resolved = repository.getProduct(previousId)
        assertEquals(ProductId("picnic:nl:s1"), resolved?.product?.id)
        assertEquals(listOf(previousId), resolved?.product?.previousIds)

        assertEquals(3, repository.rebuildProjections())
        assertEquals(resolved, repository.getProduct(previousId))
    }

    @Test
    fun historicalPricesAreIdempotentFilteredAndChronological() = runTest {
        val later = fixtureHistoricalPrice().copy(
            id = com.groceryautomate.catalog.HistoricalPriceObservationId("history-2"),
            purchasedAt = "2025-01-02T10:00:00Z"
        )
        repository.append(historicalPriceAppend(later).copy(
            commandId = CommandId("00000000-0000-4000-8000-000000000008"),
            correlationId = CommandId("00000000-0000-4000-8000-000000000008"),
            events = listOf(
                ProposedCatalogEvent(
                    EventId("00000000-0000-4000-8000-000000000009"),
                    later.purchasedAt,
                    com.groceryautomate.events.HistoricalPriceObserved(later)
                )
            )
        ))
        val first = repository.append(historicalPriceAppend())
        val duplicate = repository.append(historicalPriceAppend())

        val history = repository.getPriceHistory(ProductId("picnic:nl:s1"), 10)
        assertEquals(listOf("history-1", "history-2"), history.observations.map { it.id.value })
        assertEquals(first.copy(duplicateCommand = true), duplicate)
        assertTrue(repository.getPriceHistory(ProductId("picnic:nl:missing")).observations.isEmpty())
    }

    @Test
    fun migrationsAreRepeatableAndChecksumProtected() {
        assertEquals(0, PostgresMigrator(dataSource).migrate())

        assertFailsWith<IllegalStateException> {
            PostgresMigrator(dataSource, listOf(SqlMigration(1, "event_store", "SELECT 1"))).migrate()
        }
    }

    private companion object {
        @JvmField
        val postgres = PostgreSQLContainer("postgres:18.4-alpine")
            .withDatabaseName("grocery_test")
            .withUsername("grocery")
            .withPassword("grocery-test")
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\s", 2))

        @BeforeAll
        @JvmStatic
        fun startPostgres() {
            postgres.start()
        }

    }
}

package com.groceryautomate.backend

import com.groceryautomate.catalog.CatalogProduct
import com.groceryautomate.events.AppendResult
import com.groceryautomate.events.EventPage
import com.groceryautomate.events.ProductImportService
import com.groceryautomate.postgres.ManagedPostgresDataSource
import com.groceryautomate.postgres.PostgresCatalogEventRepository
import com.groceryautomate.postgres.PostgresDataSource
import com.groceryautomate.postgres.PostgresMigrator
import com.groceryautomate.postgres.PostgresSettings
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeAll
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Testcontainers(disabledWithoutDocker = true)
class CatalogComponentTest {
    @Test
    fun providerCommandPersistsEventsProjectionAndCursorThroughHttp() {
        val dataSource = createDataSource()
        try {
            resetDatabase(dataSource)
            val repository = PostgresCatalogEventRepository(dataSource)
            val provider = FakeProvider()
            val gateway = ProviderCatalogGateway(provider)
            val eventIds = ArrayDeque(
                listOf(
                    "00000000-0000-4000-8000-000000000002",
                    "00000000-0000-4000-8000-000000000003"
                )
            )
            val imports = ProductImportService(
                gateway,
                repository,
                { eventIds.removeFirst() },
                { "2026-08-04T12:00:00Z" }
            )
            testApplication {
                application { catalogModule(repository, gateway, imports, repository::isReady) }

                val first = client.post("/api/v1/retailers/picnic/products/s1001/imports") {
                    header("Idempotency-Key", "00000000-0000-4000-8000-000000000001")
                }
                val duplicate = client.post("/api/v1/retailers/picnic/products/s1001/imports") {
                    header("Idempotency-Key", "00000000-0000-4000-8000-000000000001")
                }
                val detail = client.get("/api/v1/catalog/products/picnic:nl:s1001")
                val events = client.get("/api/v1/events?after=0&limit=10")

                assertEquals(HttpStatusCode.Accepted, first.status)
                assertTrue(Json.decodeFromString<AppendResult>(duplicate.bodyAsText()).duplicateCommand)
                assertEquals(1, provider.getCalls)
                assertEquals(testProduct, Json.decodeFromString<CatalogProduct>(detail.bodyAsText()))
                assertEquals(2, Json.decodeFromString<EventPage>(events.bodyAsText()).events.size)
                assertEquals(HttpStatusCode.OK, client.get("/health/ready").status)
            }
        } finally {
            dataSource.close()
        }
    }

    private fun createDataSource(): ManagedPostgresDataSource {
        val dataSource = PostgresDataSource.create(
            PostgresSettings(postgres.jdbcUrl, postgres.username, postgres.password, maximumPoolSize = 2)
        )
        var failure: Throwable? = null
        repeat(100) {
            runCatching { dataSource.connection.use { connection -> connection.isValid(1) } }
                .onSuccess { if (it) return dataSource }
                .onFailure { failure = it }
            Thread.sleep(100)
        }
        dataSource.close()
        throw IllegalStateException("PostgreSQL component container was not ready.", failure)
    }

    private fun resetDatabase(dataSource: ManagedPostgresDataSource) {
        dataSource.connection.use { connection ->
            connection.createStatement().use {
                it.execute("DROP SCHEMA public CASCADE")
                it.execute("CREATE SCHEMA public")
            }
        }
        PostgresMigrator(dataSource).migrate()
    }

    private companion object {
        @JvmField
        val postgres = PostgreSQLContainer("postgres:18.4-alpine")
            .withDatabaseName("grocery_component")
            .withUsername("grocery")
            .withPassword("grocery-component")
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\s", 2))

        @BeforeAll
        @JvmStatic
        fun startPostgres() = postgres.start()
    }
}

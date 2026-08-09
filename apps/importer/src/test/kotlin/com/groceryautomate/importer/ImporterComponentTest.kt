package com.groceryautomate.importer

import com.groceryautomate.catalog.AvailabilityStatus
import com.groceryautomate.catalog.CatalogProduct
import com.groceryautomate.catalog.Money
import com.groceryautomate.catalog.Product
import com.groceryautomate.catalog.ProductCatalogPort
import com.groceryautomate.catalog.ProductId
import com.groceryautomate.catalog.ProductOffer
import com.groceryautomate.catalog.ProductOfferId
import com.groceryautomate.catalog.ProductSearchResult
import com.groceryautomate.catalog.ProviderEvidence
import com.groceryautomate.catalog.ProviderRouteGeneration
import com.groceryautomate.catalog.RetailerId
import com.groceryautomate.events.ProductImportService
import com.groceryautomate.postgres.PostgresCatalogEventRepository
import com.groceryautomate.postgres.PostgresDataSource
import com.groceryautomate.postgres.PostgresMigrator
import com.groceryautomate.postgres.PostgresSettings
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeAll
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import kotlin.test.Test
import kotlin.test.assertEquals

@Testcontainers(disabledWithoutDocker = true)
class ImporterComponentTest {
    @Test
    fun batchPersistsEventsAndRebuildableCatalogProjection() = runTest {
        val dataSource = PostgresDataSource.create(
            PostgresSettings(container.jdbcUrl, container.username, container.password, 2, 5_000)
        )
        try {
            PostgresMigrator(dataSource).migrate()
            val repository = PostgresCatalogEventRepository(dataSource)
            val eventIds = ArrayDeque(listOf(EVENT_ID_1, EVENT_ID_2))
            val importer = BatchProductImporter(
                ProductImportService(ComponentProvider(), repository, eventIds::removeFirst) { OBSERVED_AT }
            )

            val report = importer.run(manifest("s1001"))
            val events = repository.readEvents(0, 10)
            val projected = repository.getProduct(ProductId("picnic:nl:s1001"))
            val rebuilt = repository.rebuildProjections()

            assertEquals(true, report.successful)
            assertEquals(2, events.events.size)
            assertEquals("Fixture product", projected?.product?.name)
            assertEquals(2, rebuilt)
            assertEquals(projected, repository.getProduct(ProductId("picnic:nl:s1001")))
        } finally {
            dataSource.close()
        }
    }

    companion object {
        private val container = PostgreSQLContainer("postgres:18.4-alpine")
            .waitingFor(Wait.forListeningPort())

        @JvmStatic
        @BeforeAll
        fun startPostgres() {
            container.start()
        }
    }
}

private class ComponentProvider : ProductCatalogPort {
    override suspend fun search(query: String, limit: Int): ProductSearchResult =
        ProductSearchResult(query, 0, emptyList())

    override suspend fun getProduct(id: ProductId): CatalogProduct {
        val canonicalId = ProductId("picnic:nl:${id.value}")
        val evidence = ProviderEvidence(
            "picnic",
            id.value,
            "/pages/product-details-page-root",
            "nl",
            OBSERVED_AT,
            15,
            ProviderRouteGeneration.CURRENT
        )
        return CatalogProduct(
            Product(canonicalId, "Fixture product", null, null, null),
            null,
            listOf(
                ProductOffer(
                    ProductOfferId("${canonicalId.value}:current"),
                    canonicalId,
                    RetailerId("picnic"),
                    "nl",
                    Money(199, "EUR"),
                    null,
                    emptyList(),
                    null,
                    AvailabilityStatus.UNKNOWN,
                    evidence
                )
            ),
            evidence
        )
    }
}

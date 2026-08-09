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
import com.groceryautomate.events.AppendCatalogEvents
import com.groceryautomate.events.AppendResult
import com.groceryautomate.events.CatalogEventRepository
import com.groceryautomate.events.CommandId
import com.groceryautomate.events.EventPage
import com.groceryautomate.events.OfferObserved
import com.groceryautomate.events.ProductImportService
import com.groceryautomate.events.ProductImported
import com.groceryautomate.events.StreamId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BatchProductImporterTest {
    @Test
    fun importsEventsAndResumesTheSameBatchIdempotently() = runTest {
        val repository = RecordingRepository()
        val eventIds = ArrayDeque(listOf(EVENT_ID_1, EVENT_ID_2))
        val importer = BatchProductImporter(
            ProductImportService(FakeProvider(), repository, eventIds::removeFirst) { OBSERVED_AT }
        )
        val manifest = manifest("s1001")

        val first = importer.run(manifest)
        val resumed = importer.run(manifest)

        assertEquals(ImportStatus.IMPORTED, first.results.single().status)
        assertEquals(ImportStatus.ALREADY_IMPORTED, resumed.results.single().status)
        assertEquals(1, repository.appends.size)
        assertTrue(repository.appends.single().events[0].event is ProductImported)
        assertTrue(repository.appends.single().events[1].event is OfferObserved)
    }

    @Test
    fun continuesAfterMissingAndFailedProductsAndReportsFailure() = runTest {
        val repository = RecordingRepository()
        val importer = BatchProductImporter(
            ProductImportService(FakeProvider(setOf("failed")), repository, { EVENT_ID_1 }) { OBSERVED_AT }
        )
        val manifest = ImportManifest(
            1,
            "batch",
            "importer",
            listOf("missing", "failed", "s1001").map { ImportProduct(ImportRetailer.PICNIC, it) }
        )

        val report = importer.run(manifest)

        assertEquals(listOf(ImportStatus.NOT_FOUND, ImportStatus.FAILED, ImportStatus.IMPORTED), report.results.map { it.status })
        assertEquals(2, report.failureCount)
        assertEquals(false, report.successful)
        assertEquals(1, repository.appends.size)
    }
}

private class FakeProvider(private val failures: Set<String> = emptySet()) : ProductCatalogPort {
    override suspend fun search(query: String, limit: Int): ProductSearchResult =
        ProductSearchResult(query, 0, emptyList())

    override suspend fun getProduct(id: ProductId): CatalogProduct? {
        if (id.value in failures) error("Provider detail must be redacted by the importer.")
        if (id.value == "missing") return null
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

private class RecordingRepository : CatalogEventRepository {
    val appends = mutableListOf<AppendCatalogEvents>()
    private val commands = mutableMapOf<CommandId, AppendResult>()

    override suspend fun findCommand(commandId: CommandId): AppendResult? =
        commands[commandId]?.copy(duplicateCommand = true)

    override suspend fun streamVersion(streamId: StreamId): Long =
        appends.filter { it.streamId == streamId }.sumOf { it.events.size }.toLong()

    override suspend fun append(request: AppendCatalogEvents): AppendResult {
        appends += request
        val version = request.expectedVersion + request.events.size
        return AppendResult(request.streamId, version, 1, request.events.size.toLong(), request.events.size, false)
            .also { commands[request.commandId] = it }
    }

    override suspend fun search(query: String, limit: Int): ProductSearchResult =
        ProductSearchResult(query, 0, emptyList())

    override suspend fun getProduct(id: ProductId): CatalogProduct? = null
    override suspend fun readEvents(after: Long, limit: Int): EventPage = EventPage(after, after, emptyList())
    override suspend fun rebuildProjections(): Int = 0
}

internal fun manifest(productId: String) = ImportManifest(
    1,
    "batch",
    "importer",
    listOf(ImportProduct(ImportRetailer.PICNIC, productId))
)

internal const val OBSERVED_AT = "2026-08-09T10:00:00Z"
internal const val EVENT_ID_1 = "00000000-0000-4000-8000-000000000001"
internal const val EVENT_ID_2 = "00000000-0000-4000-8000-000000000002"

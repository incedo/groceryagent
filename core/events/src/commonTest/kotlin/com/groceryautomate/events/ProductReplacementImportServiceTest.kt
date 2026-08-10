package com.groceryautomate.events

import com.groceryautomate.catalog.AvailabilityStatus
import com.groceryautomate.catalog.CatalogProduct
import com.groceryautomate.catalog.HistoricalProductReference
import com.groceryautomate.catalog.Money
import com.groceryautomate.catalog.Product
import com.groceryautomate.catalog.ProductCatalogPort
import com.groceryautomate.catalog.ProductId
import com.groceryautomate.catalog.ProductOffer
import com.groceryautomate.catalog.ProductOfferId
import com.groceryautomate.catalog.ProductPriceHistory
import com.groceryautomate.catalog.ProductSearchResult
import com.groceryautomate.catalog.ProviderEvidence
import com.groceryautomate.catalog.ProviderRouteGeneration
import com.groceryautomate.catalog.RetailerId
import com.groceryautomate.catalog.parsePackageQuantity
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProductReplacementImportServiceTest {
    @Test
    fun importsNewDetailsAndLinksPreviousIdInOneCommand() {
        runSuspend {
            val repository = ReplacementRepository()
            val provider = ReplacementProvider()
            val service = service(provider, repository)

            val result = service.importReplacement(reference(), COMMAND_ID, PRODUCER_ID)

            assertEquals(ProductReplacementImportStatus.IMPORTED, result.status)
            assertEquals(CURRENT_ID, result.currentProductId)
            assertEquals(3, result.eventCount)
            assertEquals(1, provider.detailCalls)
            assertIs<ProductImported>(repository.appended!!.events[0].event)
            assertIs<OfferObserved>(repository.appended!!.events[1].event)
            val link = assertIs<PreviousProductIdLinked>(repository.appended!!.events[2].event)
            assertEquals(PREVIOUS_ID, link.previousProductId)
            assertEquals(CURRENT_ID, link.productId)
        }
    }

    @Test
    fun linksAnExistingProductWithoutFetchingDetailsAgain() {
        runSuspend {
            val repository = ReplacementRepository(existing = fixtureProduct())
            val provider = ReplacementProvider()

            val result = service(provider, repository).importReplacement(reference(), COMMAND_ID, PRODUCER_ID)

            assertEquals(ProductReplacementImportStatus.LINKED_EXISTING, result.status)
            assertEquals(0, provider.detailCalls)
            assertEquals(1, result.eventCount)
            assertIs<PreviousProductIdLinked>(repository.appended!!.events.single().event)
        }
    }

    @Test
    fun duplicateCommandDoesNotCallTheProvider() {
        runSuspend {
            val repository = ReplacementRepository(
                duplicate = AppendResult(StreamId("product:${CURRENT_ID.value}"), 3, 1, 3, 3, true)
            )
            val provider = ReplacementProvider()

            val result = service(provider, repository).importReplacement(reference(), COMMAND_ID, PRODUCER_ID)

            assertEquals(ProductReplacementImportStatus.ALREADY_IMPORTED, result.status)
            assertEquals(CURRENT_ID, result.currentProductId)
            assertEquals(0, provider.searchCalls)
        }
    }
}

private fun <T> runSuspend(block: suspend () -> T): T {
    var completed: Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<T>) {
            completed = result
        }
    })
    return checkNotNull(completed) { "Test coroutine did not complete synchronously." }.getOrThrow()
}

private fun service(provider: ProductCatalogPort, repository: CatalogEventRepository): ProductReplacementImportService {
    val eventIds = ArrayDeque(EVENT_ID_VALUES)
    return ProductReplacementImportService(provider, repository, { eventIds.removeFirst() }) { OBSERVED_AT }
}

private fun reference() = HistoricalProductReference(PREVIOUS_ID, "Kiphaasjes", "300 gram")

private class ReplacementProvider : ProductCatalogPort {
    var searchCalls = 0
    var detailCalls = 0

    override suspend fun search(query: String, limit: Int): ProductSearchResult {
        searchCalls++
        return ProductSearchResult(query, 1, listOf(fixtureProduct(composition = null)))
    }

    override suspend fun getProduct(id: ProductId): CatalogProduct {
        detailCalls++
        return fixtureProduct()
    }
}

private class ReplacementRepository(
    private val existing: CatalogProduct? = null,
    private val duplicate: AppendResult? = null
) : CatalogEventRepository {
    var appended: AppendCatalogEvents? = null

    override suspend fun findCommand(commandId: CommandId): AppendResult? = duplicate
    override suspend fun streamVersion(streamId: StreamId): Long = 0
    override suspend fun append(request: AppendCatalogEvents): AppendResult {
        appended = request
        return AppendResult(request.streamId, request.events.size.toLong(), 1, request.events.size.toLong(), request.events.size, false)
    }
    override suspend fun search(query: String, limit: Int): ProductSearchResult =
        ProductSearchResult(query, 0, emptyList())
    override suspend fun getProduct(id: ProductId): CatalogProduct? = existing?.takeIf { it.product.id == id }
    override suspend fun getPriceHistory(productId: ProductId, limit: Int): ProductPriceHistory =
        ProductPriceHistory(productId, emptyList())
    override suspend fun readEvents(after: Long, limit: Int): EventPage = EventPage(after, after, emptyList())
    override suspend fun rebuildProjections(): Int = 0
}

private fun fixtureProduct(composition: com.groceryautomate.catalog.ProductComposition? = null): CatalogProduct {
    val evidence = ProviderEvidence(
        "picnic",
        "s-new",
        "/pages/search-page-root-content",
        "nl",
        OBSERVED_AT,
        15,
        ProviderRouteGeneration.CURRENT
    )
    return CatalogProduct(
        Product(CURRENT_ID, "Kiphaasjes", "Slager", null, "image-new"),
        composition,
        listOf(
            ProductOffer(
                ProductOfferId("${CURRENT_ID.value}:current"),
                CURRENT_ID,
                RetailerId("picnic"),
                "nl",
                Money(499, "EUR"),
                parsePackageQuantity("300 gram"),
                emptyList(),
                null,
                AvailabilityStatus.UNKNOWN,
                evidence
            )
        ),
        evidence
    )
}

private val PREVIOUS_ID = ProductId("picnic:nl:s-old")
private val CURRENT_ID = ProductId("picnic:nl:s-new")
private val COMMAND_ID = CommandId("00000000-0000-4000-8000-000000000101")
private val PRODUCER_ID = ProducerId("replacement-test")
private const val OBSERVED_AT = "2026-08-10T00:00:00Z"
private val EVENT_ID_VALUES = listOf(
    "00000000-0000-4000-8000-000000000201",
    "00000000-0000-4000-8000-000000000202",
    "00000000-0000-4000-8000-000000000203"
)

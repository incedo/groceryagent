package com.groceryautomate.importer

import com.groceryautomate.catalog.ProductId
import com.groceryautomate.catalog.ProductImageAsset
import com.groceryautomate.catalog.ProductImageAssetPort
import com.groceryautomate.catalog.ProductImageImportCandidate
import com.groceryautomate.catalog.ProductImageObject
import com.groceryautomate.catalog.ProductImageObjectStore
import com.groceryautomate.catalog.ProductImageSource
import com.groceryautomate.catalog.ProductImageVariant
import com.groceryautomate.catalog.ProductPriceHistory
import com.groceryautomate.catalog.ProductSearchResult
import com.groceryautomate.events.AppendCatalogEvents
import com.groceryautomate.events.AppendResult
import com.groceryautomate.events.CatalogEventRepository
import com.groceryautomate.events.CommandId
import com.groceryautomate.events.EventPage
import com.groceryautomate.events.ProductImageImportService
import com.groceryautomate.events.StreamId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BatchProductImageImporterTest {
    @Test
    fun storesBoundedPngAppendsEventAndPacesCandidates() = runTest {
        val repository = ImageRepository()
        var uploads = 0
        var delays = 0
        val importer = BatchProductImageImporter(
            source = object : ProductImageSource {
                override suspend fun getPng(sourceImageId: String, variant: ProductImageVariant) =
                    PNG_SIGNATURE
            },
            objectStore = object : ProductImageObjectStore {
                override suspend fun putPng(sha256: String, bytes: ByteArray): ProductImageObject {
                    uploads++
                    return ProductImageObject(
                        "product-images",
                        "images/$sha256.png",
                        "https://assets.example.test/product-images/images/$sha256.png"
                    )
                }
            },
            assets = repository,
            events = ProductImageImportService(repository) { EVENT_ID },
            now = { OCCURRED_AT },
            awaitNextImage = { delays++ }
        )

        val report = importer.run(2)

        assertEquals(listOf(ImageImportStatus.STORED, ImageImportStatus.STORED), report.results.map { it.status })
        assertEquals(2, uploads)
        assertEquals(1, delays)
        assertEquals(2, repository.appends.size)
        assertEquals("ProductImageStored", repository.appends.first().events.single().event.eventType)
    }

    @Test
    fun reportsTheFailingExternalStageWithoutLoggingItsMessage() = runTest {
        val repository = ImageRepository()
        val importer = BatchProductImageImporter(
            source = object : ProductImageSource {
                override suspend fun getPng(sourceImageId: String, variant: ProductImageVariant) =
                    PNG_SIGNATURE
            },
            objectStore = object : ProductImageObjectStore {
                override suspend fun putPng(sha256: String, bytes: ByteArray): ProductImageObject =
                    error("sensitive provider detail")
            },
            assets = repository,
            events = ProductImageImportService(repository) { EVENT_ID },
            now = { OCCURRED_AT }
        )

        val result = importer.run(1).results.single()

        assertEquals(ImageImportStatus.FAILED, result.status)
        assertEquals("ProductImageStorageException", result.failure?.exceptionType)
        assertIs<ImportFailureDiagnostic>(result.failure)
    }

    private companion object {
        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
        )
        const val EVENT_ID = "00000000-0000-4000-8000-000000000021"
        const val OCCURRED_AT = "2026-08-10T10:00:00Z"
    }
}

private class ImageRepository : CatalogEventRepository, ProductImageAssetPort {
    val appends = mutableListOf<AppendCatalogEvents>()
    private val candidates = listOf(
        ProductImageImportCandidate(ProductId("picnic:nl:s1"), "image-1"),
        ProductImageImportCandidate(ProductId("picnic:nl:s2"), "image-2")
    )

    override suspend fun findImageImportCandidates(variant: ProductImageVariant, limit: Int) =
        candidates.take(limit)

    override suspend fun getProductImageAsset(productId: ProductId, variant: ProductImageVariant): ProductImageAsset? =
        null

    override suspend fun findCommand(commandId: CommandId): AppendResult? = null
    override suspend fun streamVersion(streamId: StreamId): Long = 2

    override suspend fun append(request: AppendCatalogEvents): AppendResult {
        appends += request
        return AppendResult(request.streamId, 3, 1, 1, 1, false)
    }

    override suspend fun readEvents(after: Long, limit: Int) = EventPage(after, after, emptyList())
    override suspend fun rebuildProjections(): Int = 0
    override suspend fun search(query: String, limit: Int) = ProductSearchResult(query, 0, emptyList())
    override suspend fun getProduct(id: ProductId) = null
    override suspend fun getPriceHistory(productId: ProductId, limit: Int) =
        ProductPriceHistory(productId, emptyList())
}

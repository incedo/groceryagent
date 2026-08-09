package com.groceryautomate.importer

import com.groceryautomate.catalog.HistoricalPriceObservation
import com.groceryautomate.catalog.HistoricalPriceObservationId
import com.groceryautomate.catalog.Money
import com.groceryautomate.catalog.ProductId
import com.groceryautomate.catalog.RetailerId
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ImportManifestSplitterTest {
    @Test
    fun splitsProductsAndAssignsPricesWithoutChangingOrder() {
        val parent = createTempDirectory("manifest-split-")
        val source = parent.resolve("source.json")
        val manifest = sourceManifest()
        OrderCaptureFiles().writeManifest(source, manifest)

        val index = ImportManifestSplitter().split(source, parent.resolve("shards"), 2)

        assertEquals(2, index.shards.size)
        assertEquals(DEFAULT_MAX_MANIFEST_BYTES, index.maxBytesPerShard)
        assertEquals(listOf("orders-part-001", "orders-part-002"), index.shards.map { it.batchId })
        val first = ImportManifestFile.read(parent.resolve("shards/manifest-part-001.json"))
        val second = ImportManifestFile.read(parent.resolve("shards/manifest-part-002.json"))
        assertEquals(listOf("s1", "s2"), first.products.map { it.productId })
        assertEquals(listOf("price-s1", "price-s2"), first.historicalPrices.map { it.id.value })
        assertEquals(listOf("s3"), second.products.map { it.productId })
        assertEquals(listOf("price-s3"), second.historicalPrices.map { it.id.value })
        assertEquals(sha256Hex(Files.readAllBytes(source)), index.sourceSha256)
        index.shards.forEach { shard ->
            val bytes = Files.readAllBytes(parent.resolve("shards").resolve(shard.fileName))
            assertEquals(sha256Hex(bytes), shard.sha256)
        }
    }

    @Test
    fun createsByteStableShardsAndIndex() {
        val parent = createTempDirectory("manifest-stable-")
        val source = parent.resolve("source.json")
        OrderCaptureFiles().writeManifest(source, sourceManifest())

        ImportManifestSplitter().split(source, parent.resolve("first"), 2)
        ImportManifestSplitter().split(source, parent.resolve("second"), 2)

        listOf("manifest-part-001.json", "manifest-part-002.json", "manifest-index.json").forEach {
            assertEquals(
                Files.readAllBytes(parent.resolve("first/$it")).toList(),
                Files.readAllBytes(parent.resolve("second/$it")).toList()
            )
        }
    }

    @Test
    fun startsANewShardBeforeTheByteCeilingIsExceeded() {
        val parent = createTempDirectory("manifest-bytes-")
        val source = parent.resolve("source.json")
        OrderCaptureFiles().writeManifest(source, sourceManifest())
        val baseline = ImportManifestSplitter().split(source, parent.resolve("baseline"), 1)
        val largestSingle = baseline.shards.maxOf { shard ->
            Files.size(parent.resolve("baseline").resolve(shard.fileName)).toInt()
        }

        val limited = ImportManifestSplitter().split(
            source, parent.resolve("limited"), 3, largestSingle
        )

        assertEquals(3, limited.shards.size)
        limited.shards.forEach { shard ->
            val size = Files.size(parent.resolve("limited").resolve(shard.fileName))
            assertEquals(true, size <= largestSingle)
        }
    }

    @Test
    fun rejectsUnmatchedPricesBeforeCreatingOutput() {
        val parent = createTempDirectory("manifest-unmatched-")
        val source = parent.resolve("source.json")
        val manifest = sourceManifest().copy(
            historicalPrices = listOf(price("missing", "price-missing"))
        )
        OrderCaptureFiles().writeManifest(source, manifest)
        val output = parent.resolve("shards")

        assertFailsWith<IllegalArgumentException> {
            ImportManifestSplitter().split(source, output, 2)
        }
        assertFalse(Files.exists(output))
    }

    @Test
    fun rejectsInvalidSizeAndExistingOutput() {
        val parent = createTempDirectory("manifest-invalid-")
        val source = parent.resolve("source.json")
        OrderCaptureFiles().writeManifest(source, sourceManifest())
        assertFailsWith<IllegalArgumentException> {
            ImportManifestSplitter().split(source, parent.resolve("zero"), 0)
        }
        assertFailsWith<IllegalArgumentException> {
            ImportManifestSplitter().split(source, parent.resolve("tiny"), 2, 1)
        }
        val output = parent.resolve("existing")
        Files.createDirectory(output)
        assertFailsWith<IllegalArgumentException> {
            ImportManifestSplitter().split(source, output, 2)
        }
    }
}

private fun sourceManifest() = ImportManifest(
    schemaVersion = IMPORT_MANIFEST_SCHEMA_VERSION,
    batchId = "orders",
    producerId = "splitter-test",
    products = listOf("s1", "s2", "s3").map { ImportProduct(ImportRetailer.PICNIC, it) },
    historicalPrices = listOf(
        price("s3", "price-s3"),
        price("s1", "price-s1"),
        price("s2", "price-s2")
    )
)

private fun price(productId: String, observationId: String) = HistoricalPriceObservation(
    id = HistoricalPriceObservationId(observationId),
    productId = ProductId("picnic:nl:$productId"),
    retailerId = RetailerId("picnic"),
    region = "nl",
    paidLineTotal = Money(199, "EUR"),
    originalLineTotal = null,
    quantity = 1,
    packageText = "1 item",
    promotionLabel = null,
    purchasedAt = "2026-08-09T12:00:00Z",
    source = "splitter-test"
)

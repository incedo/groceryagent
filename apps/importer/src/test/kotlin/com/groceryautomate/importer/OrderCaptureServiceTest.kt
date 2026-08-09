package com.groceryautomate.importer

import com.groceryautomate.picnic.application.port.`in`.PicnicDeliveryPort
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OrderCaptureServiceTest {
    @Test
    fun capturesSummaryAndDetailsThenGeneratesDeduplicatedManifest() = runTest {
        val parent = Files.createTempDirectory("order-capture-test")
        val capture = parent.resolve("capture")
        val manifestFile = parent.resolve("manifest.json")
        try {
            val provider = FakeDeliveryPort()
            val result = OrderCaptureService(provider).captureCompletedOrders(capture)
            val manifest = OrderCaptureFiles().toManifest(capture, manifestFile, "orders-2026-08-09")

            assertEquals(2, result.deliveryCount)
            assertEquals(listOf("COMPLETED"), provider.filters)
            assertEquals(listOf("delivery-1", "delivery-2"), provider.detailRequests)
            assertTrue(Files.isRegularFile(capture.resolve("summary.json")))
            assertTrue(Files.isRegularFile(capture.resolve("delivery-001.json")))
            assertTrue(Files.isRegularFile(capture.resolve("capture-complete.json")))
            assertEquals(listOf("s1001", "s1002"), manifest.products.map { it.productId })
            assertEquals(manifest, ImportManifestFile.read(manifestFile))
            assertEquals(0, provider.mutationCalls)
        } finally {
            parent.toFile().deleteRecursively()
        }
    }

    @Test
    fun refusesOverwriteAndUnknownProductShapes() = runTest {
        val parent = Files.createTempDirectory("order-capture-empty-test")
        val capture = parent.resolve("capture")
        try {
            OrderCaptureService(FakeDeliveryPort()).captureCompletedOrders(capture)

            assertFailsWith<IllegalArgumentException> {
                OrderCaptureService(FakeDeliveryPort()).captureCompletedOrders(capture)
            }
            Files.writeString(capture.resolve("delivery-001.json"), "{\"description\":\"s1001\"}")
            Files.writeString(capture.resolve("delivery-002.json"), "{\"name\":\"No product id\"}")
            assertFailsWith<IllegalArgumentException> {
                OrderCaptureFiles().toManifest(capture, parent.resolve("empty.json"), "empty-batch")
            }
        } finally {
            parent.toFile().deleteRecursively()
        }
    }

    @Test
    fun repositoryRelativePrivateOutputMustStayBelowLocalDirectory() {
        assertFailsWith<IllegalArgumentException> {
            OrderCaptureFiles().createCaptureDirectory(java.nio.file.Path.of("captures/private-orders"))
        }
    }

    @Test
    fun incompleteCaptureCannotProduceManifest() {
        val parent = Files.createTempDirectory("order-capture-partial-test")
        val capture = parent.resolve("capture")
        try {
            OrderCaptureFiles().createCaptureDirectory(capture)
            OrderCaptureFiles().writeJson(
                capture.resolve("delivery-001.json"),
                Json.parseToJsonElement("""{"id":"s1001"}""")
            )

            assertFailsWith<IllegalArgumentException> {
                OrderCaptureFiles().toManifest(capture, parent.resolve("manifest.json"), "partial")
            }
        } finally {
            parent.toFile().deleteRecursively()
        }
    }
}

private class FakeDeliveryPort : PicnicDeliveryPort {
    var filters: List<String>? = null
    val detailRequests = mutableListOf<String>()
    var mutationCalls: Int = 0

    override suspend fun getDeliveries(filter: List<String>): JsonElement {
        filters = filter
        return Json.parseToJsonElement("""[{"id":"delivery-1"},{"delivery_id":"delivery-2"}]""")
    }

    override suspend fun getDelivery(deliveryId: String): JsonElement {
        detailRequests += deliveryId
        return when (deliveryId) {
            "delivery-1" -> Json.parseToJsonElement(
                """{"items":[{"id":"s1001"},{"product_id":"s1002"}]}"""
            )
            else -> Json.parseToJsonElement("""{"items":[{"articleId":"s1002"}]}""")
        }
    }

    override suspend fun getDeliveryPosition(deliveryId: String): JsonElement = unexpectedRead()
    override suspend fun getDeliveryScenario(deliveryId: String): JsonElement = unexpectedRead()

    override suspend fun cancelDelivery(deliveryId: String): JsonElement = unexpectedMutation()
    override suspend fun setDeliveryRating(deliveryId: String, rating: Int): JsonElement = unexpectedMutation()
    override suspend fun sendDeliveryInvoiceEmail(deliveryId: String): JsonElement = unexpectedMutation()

    private fun unexpectedRead(): Nothing = error("Capture must not call unrelated delivery reads.")
    private fun unexpectedMutation(): Nothing {
        mutationCalls++
        error("Capture must never call a delivery mutation.")
    }
}

package com.groceryautomate.importer

import com.groceryautomate.picnic.application.port.`in`.PicnicDeliveryPort
import com.groceryautomate.picnic.domain.PicnicOrderReferenceExtractor
import java.nio.file.Path

class OrderCaptureService(
    private val deliveries: PicnicDeliveryPort,
    private val files: OrderCaptureFiles = OrderCaptureFiles()
) {
    suspend fun captureCompletedOrders(directory: Path): OrderCaptureResult {
        files.createCaptureDirectory(directory)
        val summary = deliveries.getDeliveries(listOf("COMPLETED"))
        files.writeJson(directory.resolve("summary.json"), summary)
        val deliveryIds = PicnicOrderReferenceExtractor.deliveryIds(summary)
        require(deliveryIds.isNotEmpty()) {
            "Picnic returned no recognized completed delivery ids; summary was retained for local inspection."
        }
        deliveryIds.forEachIndexed { index, deliveryId ->
            val detail = deliveries.getDelivery(deliveryId)
            files.writeJson(directory.resolve("delivery-${(index + 1).toString().padStart(3, '0')}.json"), detail)
        }
        files.markCaptureComplete(directory, deliveryIds.size)
        return OrderCaptureResult(directory, deliveryIds.size)
    }
}

data class OrderCaptureResult(
    val directory: Path,
    val deliveryCount: Int
)

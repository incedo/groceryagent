package com.groceryautomate.picnic.application.service

import com.groceryautomate.picnic.adapter.out.http.PicnicRequester
import com.groceryautomate.picnic.adapter.out.http.encodePath
import com.groceryautomate.picnic.application.port.`in`.PicnicDeliveryPort
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class DeliveryService(
    private val requester: PicnicRequester
) : PicnicDeliveryPort {
    override suspend fun getDeliveries(filter: List<String>): JsonElement = requester.json(
        "POST",
        "/deliveries/summary",
        buildJsonArray { filter.forEach { add(JsonPrimitive(it)) } }
    )

    override suspend fun getDelivery(deliveryId: String): JsonElement =
        requester.json("GET", "/deliveries/${requiredId(deliveryId, "Delivery")}")

    override suspend fun getDeliveryPosition(deliveryId: String): JsonElement = requester.json(
        "GET",
        "/deliveries/${requiredId(deliveryId, "Delivery")}/position"
    )

    override suspend fun getDeliveryScenario(deliveryId: String): JsonElement = requester.json(
        "GET",
        "/deliveries/${requiredId(deliveryId, "Delivery")}/scenario"
    )

    override suspend fun cancelDelivery(deliveryId: String): JsonElement =
        requester.json("POST", "/order/delivery/${requiredId(deliveryId, "Delivery")}/cancel")

    override suspend fun setDeliveryRating(deliveryId: String, rating: Int): JsonElement {
        require(rating in 0..10) { "Delivery rating must be between 0 and 10." }
        return requester.json(
            "POST",
            "/deliveries/${requiredId(deliveryId, "Delivery")}/rating",
            buildJsonObject { put("rating", rating) }
        )
    }

    override suspend fun sendDeliveryInvoiceEmail(deliveryId: String): JsonElement = requester.json(
        "POST",
        "/deliveries/${requiredId(deliveryId, "Delivery")}/resend_invoice_email"
    )

    private fun requiredId(value: String, label: String): String {
        require(value.isNotBlank()) { "$label id must not be blank." }
        return encodePath(value)
    }
}

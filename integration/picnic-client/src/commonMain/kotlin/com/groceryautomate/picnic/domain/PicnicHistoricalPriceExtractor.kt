package com.groceryautomate.picnic.domain

import com.groceryautomate.catalog.HistoricalPriceObservation
import com.groceryautomate.catalog.HistoricalPriceObservationId
import com.groceryautomate.catalog.Money
import com.groceryautomate.catalog.ProductId
import com.groceryautomate.catalog.RetailerId
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

object PicnicHistoricalPriceExtractor {
    private val productIdPattern = Regex("^s[0-9]+$")

    fun observations(
        details: Iterable<JsonElement>,
        idForSource: (String) -> HistoricalPriceObservationId
    ): List<HistoricalPriceObservation> = buildList {
        val seen = mutableSetOf<HistoricalPriceObservationId>()
        details.forEach { detail ->
            val delivery = detail as? JsonObject ?: return@forEach
            val deliveryId = delivery.value("delivery_id") ?: delivery.value("id") ?: return@forEach
            delivery.array("orders").forEach { orderElement ->
                val order = orderElement as? JsonObject ?: return@forEach
                val orderId = order.value("id") ?: return@forEach
                val purchasedAt = order.string("creation_time") ?: return@forEach
                order.array("items").forEach { rowElement ->
                    val row = rowElement as? JsonObject ?: return@forEach
                    if (row.string("type") != "ORDER_LINE") return@forEach
                    val observation = row.toObservation(
                        deliveryId = deliveryId,
                        orderId = orderId,
                        purchasedAt = purchasedAt,
                        idForSource = idForSource
                    ) ?: return@forEach
                    if (seen.add(observation.id)) add(observation)
                }
            }
        }
    }

    private fun JsonObject.toObservation(
        deliveryId: String,
        orderId: String,
        purchasedAt: String,
        idForSource: (String) -> HistoricalPriceObservationId
    ): HistoricalPriceObservation? {
        val rowId = value("id") ?: return null
        val products = productObjects().mapNotNull { it.string("id") }
            .filter(productIdPattern::matches).distinct()
        val providerProductId = products.singleOrNull() ?: return null
        val quantities = descendantObjects()
            .filter { it.string("type") == "QUANTITY" }
            .mapNotNull { it.int("quantity") }.filter { it > 0 }.distinct()
        val quantity = quantities.singleOrNull() ?: return null
        val paid = array("decorators").mapNotNull { it as? JsonObject }
            .firstOrNull { it.string("type") == "PRICE" }?.int("display_price")
            ?: int("display_price") ?: int("price") ?: return null
        if (paid < 0) return null
        val original = int("price")?.takeIf { it >= 0 && it != paid }
        val matchingProduct = productObjects().firstOrNull { it.string("id") == providerProductId }
        val sourceKey = listOf(deliveryId, orderId, rowId, providerProductId).joinToString(":")
        return HistoricalPriceObservation(
            id = idForSource(sourceKey),
            productId = ProductId("picnic:nl:$providerProductId"),
            retailerId = RetailerId("picnic"),
            region = "nl",
            paidLineTotal = Money(paid.toLong(), "EUR"),
            originalLineTotal = original?.let { Money(it.toLong(), "EUR") },
            quantity = quantity,
            packageText = matchingProduct?.string("unit_quantity"),
            promotionLabel = array("decorators").mapNotNull { it as? JsonObject }
                .firstOrNull { it.string("type") == "PROMO" }?.string("text"),
            purchasedAt = purchasedAt,
            source = "picnic-completed-order"
        )
    }

    private fun JsonObject.productObjects(): List<JsonObject> = descendantObjects()
        .filter { it.string("id")?.let(productIdPattern::matches) == true }

    private fun JsonObject.descendantObjects(): List<JsonObject> = buildList {
        fun collect(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    add(element)
                    element.values.forEach(::collect)
                }
                is JsonArray -> element.forEach(::collect)
                else -> Unit
            }
        }
        collect(this@descendantObjects)
    }

    private fun JsonObject.array(name: String): JsonArray = this[name] as? JsonArray ?: JsonArray(emptyList())
    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf(String::isNotBlank)
    private fun JsonObject.value(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
    private fun JsonObject.int(name: String): Int? = (this[name] as? JsonPrimitive)?.intOrNull
}

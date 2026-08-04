package com.groceryautomate.picnic.application.service

import com.groceryautomate.picnic.adapter.out.http.PicnicRequester
import com.groceryautomate.picnic.adapter.out.http.encodePath
import com.groceryautomate.picnic.application.port.`in`.PicnicCartPort
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class CartService(
    private val requester: PicnicRequester
) : PicnicCartPort {
    override suspend fun getCart(): JsonElement = requester.json("GET", "/cart")

    override suspend fun addProductToCart(
        productId: String,
        count: Int,
        contexts: List<JsonObject>?
    ): JsonElement = mutateProduct("/cart/add_product", productId, count, contexts)

    override suspend fun addProductsToCart(products: Map<String, Int>): JsonElement {
        require(products.isNotEmpty()) { "Products must not be empty." }
        products.forEach { (id, quantity) ->
            requireId(id, "Product")
            require(quantity > 0) { "Product quantity must be positive." }
        }
        return requester.json(
            "POST",
            "/cart/products/add",
            buildJsonObject { products.forEach { (id, quantity) -> put(id, quantity) } }
        )
    }

    override suspend fun removeProductFromCart(
        productId: String,
        count: Int,
        contexts: List<JsonObject>?
    ): JsonElement = mutateProduct("/cart/remove_product", productId, count, contexts)

    override suspend fun clearCart(): JsonElement = requester.json("POST", "/cart/clear")

    override suspend fun getDeliverySlots(): JsonElement = requester.json("GET", "/cart/delivery_slots")

    override suspend fun setDeliverySlot(slotId: String): JsonElement {
        requireId(slotId, "Delivery slot")
        return requester.json(
            "POST",
            "/cart/set_delivery_slot",
            buildJsonObject { put("slot_id", slotId) }
        )
    }

    override suspend fun getOrderStatus(orderId: String): JsonElement {
        requireId(orderId, "Order")
        return requester.json("GET", "/cart/checkout/order/${encodePath(orderId)}/status")
    }

    override suspend fun removeGroupFromCart(groupId: String): JsonElement {
        requireId(groupId, "Group")
        return requester.json(
            "POST",
            "/cart/remove_group",
            buildJsonObject { put("group_id", groupId) }
        )
    }

    override suspend fun getMinimumOrderValue(): JsonElement =
        requester.json("GET", "/user-slot-minimum-order-value/minimum")

    override suspend fun confirmOrder(orderId: String): JsonElement {
        requireId(orderId, "Order")
        return requester.json("POST", "/cart/checkout/order/${encodePath(orderId)}/confirm")
    }

    private suspend fun mutateProduct(
        path: String,
        productId: String,
        count: Int,
        contexts: List<JsonObject>?
    ): JsonElement {
        requireId(productId, "Product")
        require(count > 0) { "Product quantity must be positive." }
        return requester.json(
            "POST",
            path,
            buildJsonObject {
                put("product_id", productId)
                put("count", count)
                contexts?.let { values ->
                    put("selling_unit_contexts", buildJsonArray { values.forEach(::add) })
                }
            }
        )
    }

    private fun requireId(value: String, label: String) {
        require(value.isNotBlank()) { "$label id must not be blank." }
    }
}

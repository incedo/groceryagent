package com.groceryautomate.picnic.application.service

import com.groceryautomate.picnic.domain.PicnicPriceRange
import com.groceryautomate.picnic.domain.PicnicProductSummary
import com.groceryautomate.picnic.domain.PicnicPromotion
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal fun extractProductSummaries(page: JsonElement): List<PicnicProductSummary> {
    val nested = page.objectsNamed("sellingUnit")
    val direct = page.allObjects().filter(JsonObject::looksLikeProduct)
    return (nested + direct)
        .mapNotNull(JsonObject::toProductSummary)
        .distinctBy(PicnicProductSummary::id)
        .toList()
}

internal fun JsonObject.toProductSummary(): PicnicProductSummary? {
    val id = stringOrNull("id")?.takeIf(::isProductId) ?: return null
    val name = stringOrNull("name") ?: return null
    val imageId = stringOrNull("image_id")
        ?: (this["image_ids"] as? JsonArray)
            ?.firstOrNull()
            ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
    val decorators = (this["decorators"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
    val decoratedPrice = decorators.firstOrNull { it.string("type") == "PRICE" }
        ?.intOrNull("display_price")
    return PicnicProductSummary(
        id = id,
        name = name,
        brand = stringOrNull("brand"),
        priceCents = intOrNull("display_price") ?: intOrNull("price") ?: decoratedPrice,
        unitQuantity = stringOrNull("unit_quantity"),
        imageId = imageId,
        maxCount = intOrNull("max_count"),
        priceRanges = priceRanges(),
        promotion = promotion(decorators)
    )
}

private fun JsonObject.looksLikeProduct(): Boolean =
    isProductId(string("id")) && stringOrNull("name") != null &&
        ("display_price" in this || "price" in this)

private fun isProductId(value: String): Boolean =
    value.length > 1 && value.first() == 's' && value.drop(1).all(Char::isDigit)

private fun JsonObject.priceRanges(): List<PicnicPriceRange> =
    (this["price_ranges"] as? JsonArray).orEmpty().mapNotNull { element ->
        val range = element as? JsonObject ?: return@mapNotNull null
        val price = range.intOrNull("price") ?: return@mapNotNull null
        val quantity = range.intOrNull("from_quantity") ?: return@mapNotNull null
        PicnicPriceRange(price, quantity)
    }

private fun JsonObject.promotion(decorators: List<JsonObject>): PicnicPromotion? {
    val value = this["promotion"] as? JsonObject
    val badge = decorators.firstOrNull { it.string("type") == "PROMO" }?.stringOrNull("text")
    val id = value?.stringOrNull("promotion_id") ?: stringOrNull("promotion_id")
    val label = value?.stringOrNull("promotion_label") ?: stringOrNull("promotion_label")
    val price = value?.intOrNull("price")
    val originalPrice = value?.intOrNull("strikethrough_price")
    val showOriginal = value?.booleanOrNull("show_strikethrough_price") ?: false
    if (id == null && label == null && price == null && originalPrice == null && badge == null) return null
    return PicnicPromotion(id, label, price, originalPrice, showOriginal, badge)
}

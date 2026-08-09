package com.groceryautomate.picnic.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

object PicnicOrderReferenceExtractor {
    private val deliveryIdFields = setOf("id", "delivery_id", "deliveryId")
    private val productIdFields = setOf(
        "id",
        "product_id",
        "productId",
        "article_id",
        "articleId",
        "selling_unit_id",
        "sellingUnitId"
    )
    private val productIdPattern = Regex("^s[0-9]+$")

    fun deliveryIds(summary: JsonElement): List<String> {
        val deliveries = summary as? JsonArray ?: return emptyList()
        return deliveries.mapNotNull { delivery ->
            val fields = delivery as? JsonObject ?: return@mapNotNull null
            deliveryIdFields.firstNotNullOfOrNull { field -> fields[field].stringValue() }
        }.distinct()
    }

    fun productIds(details: Iterable<JsonElement>): List<String> = buildList {
        val seen = mutableSetOf<String>()
        details.forEach { detail -> collectProductIds(detail, seen, this) }
    }

    private fun collectProductIds(
        element: JsonElement,
        seen: MutableSet<String>,
        destination: MutableList<String>
    ) {
        when (element) {
            is JsonArray -> element.forEach { collectProductIds(it, seen, destination) }
            is JsonObject -> element.forEach { (field, value) ->
                val candidate = value.stringValue()
                if (field in productIdFields && candidate != null && productIdPattern.matches(candidate)) {
                    if (seen.add(candidate)) destination += candidate
                }
                collectProductIds(value, seen, destination)
            }
            else -> Unit
        }
    }

    private fun JsonElement?.stringValue(): String? =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf(String::isNotBlank)
}

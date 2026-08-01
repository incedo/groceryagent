package com.groceryautomate.picnic.application.service

import com.groceryautomate.picnic.domain.PicnicAllergenDataStatus
import com.groceryautomate.picnic.domain.PicnicAllergenStatement
import com.groceryautomate.picnic.domain.PicnicDecimal
import com.groceryautomate.picnic.domain.PicnicNutrition
import com.groceryautomate.picnic.domain.PicnicNutritionBasis
import com.groceryautomate.picnic.domain.PicnicProductDetails
import com.groceryautomate.picnic.domain.PicnicProductInfoSection
import com.groceryautomate.picnic.domain.PicnicProductSummary
import com.groceryautomate.picnic.domain.PicnicPromotion
import com.groceryautomate.picnic.domain.PicnicProviderSource
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal fun extractLegacyProductDetails(
    requestedId: String,
    response: JsonElement,
    source: PicnicProviderSource
): PicnicProductDetails {
    val root = response as? JsonObject ?: mappingFailure("Legacy product response is not an object.")
    val details = root["product_details"] as? JsonObject ?: root
    val id = details.stringOrNull("product_id") ?: details.stringOrNull("id") ?: requestedId
    val name = details.stringOrNull("name")
        ?: mappingFailure("Legacy product response has no product name.")
    val imageId = details.stringOrNull("image_id")
        ?: details.stringArray("image_ids").firstOrNull()
    val promotionObject = details["promo_label"] as? JsonObject
    val promotionLabel = promotionObject?.stringOrNull("label")
    val allergens = details.explicitAllergens()
    return PicnicProductDetails(
        product = PicnicProductSummary(
            id = id,
            name = name,
            brand = details.stringOrNull("brand"),
            priceCents = details.intOrNull("display_price") ?: details.intOrNull("price"),
            unitQuantity = details.stringOrNull("unit_quantity"),
            imageId = imageId,
            maxCount = details.intOrNull("max_count"),
            priceRanges = emptyList(),
            promotion = promotionLabel?.let {
                PicnicPromotion(null, it, null, details.intOrNull("original_price"), false, it)
            }
        ),
        ingredients = details.stringOrNull("ingredients_blob"),
        allergens = allergens,
        nutrition = details.legacyNutrition(),
        preparation = emptyList(),
        storage = details.sectionText("Bewaren", "Storage"),
        description = details.stringOrNull("description"),
        originCountry = details.sectionText("Land van herkomst", "Country of origin"),
        supplier = details.sectionText("Leverancier", "Supplier"),
        highlights = emptyList(),
        extraInformation = details.stringOrNull("additional_info")
            ?.let { mapOf("additional_info" to it) }
            .orEmpty(),
        infoSections = details.infoSections(),
        bundles = emptyList(),
        similarProducts = emptyList(),
        source = source
    )
}

private fun JsonObject.explicitAllergens(): PicnicAllergenStatement {
    val value = this["allergens"] as? JsonObject
        ?: return PicnicAllergenStatement(emptyList(), emptyList(), PicnicAllergenDataStatus.UNKNOWN)
    val contains = value.stringArray("contains")
    val mayContain = value.stringArray("may_contain") + value.stringArray("mayContain")
    return PicnicAllergenStatement(
        contains.distinct(),
        mayContain.distinct(),
        PicnicAllergenDataStatus.OBSERVED
    )
}

private fun JsonObject.legacyNutrition(): PicnicNutrition? {
    val rows = (this["nutritional_values"] as? JsonArray).orEmpty()
        .filterIsInstance<JsonObject>()
        .associate { it.string("name").lowercase() to it.string("value") }
    if (rows.isEmpty()) return null
    fun value(vararg names: String): String? = names.firstNotNullOfOrNull { rows[it.lowercase()] }
    return PicnicNutrition(
        basis = when {
            string("nutritional_info_unit").contains("100 ml", true) -> PicnicNutritionBasis.PER_100_MILLILITRES
            string("nutritional_info_unit").contains("100 g", true) -> PicnicNutritionBasis.PER_100_GRAMS
            else -> PicnicNutritionBasis.UNKNOWN
        },
        energyKiloJoules = value("energie", "energy")?.firstNumber()?.toIntOrNull(),
        energyKiloCalories = value("kcal", "calories")?.firstNumber()?.toIntOrNull(),
        carbohydratesGrams = value("koolhydraten", "carbohydrates").decimal(),
        sugarsGrams = value("waarvan suikers", "sugars").decimal(),
        fatGrams = value("vet", "fat").decimal(),
        saturatedFatGrams = value("waarvan verzadigd", "saturated fat").decimal(),
        proteinGrams = value("eiwit", "protein").decimal(),
        saltGrams = value("zout", "salt").decimal(),
        fibreGrams = value("vezels", "fibre", "fiber").decimal()
    )
}

private fun JsonObject.infoSections(): List<PicnicProductInfoSection> =
    (this["items"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>().flatMap { section ->
        (section["items"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>().mapNotNull { item ->
            val title = item.stringOrNull("title") ?: return@mapNotNull null
            PicnicProductInfoSection(title, item.stringOrNull("text").orEmpty())
        }
    }

private fun JsonObject.sectionText(vararg titles: String): String? = infoSections()
    .firstOrNull { section -> titles.any { it.equals(section.title, true) } }
    ?.content
    ?.takeIf(String::isNotBlank)

private fun JsonObject.stringArray(name: String): List<String> =
    (this[name] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }

private fun String?.decimal(): PicnicDecimal? {
    val number = this?.firstNumber() ?: return null
    val parts = number.replace(',', '.').split('.', limit = 2)
    val fraction = parts.getOrElse(1) { "" }
    return (parts[0] + fraction).toLongOrNull()?.let { PicnicDecimal(it, fraction.length) }
}

private fun String.firstNumber(): String? = Regex("[0-9]+(?:[.,][0-9]+)?").find(this)?.value

private fun mappingFailure(message: String): Nothing =
    throw com.groceryautomate.picnic.domain.PicnicMappingException(message)

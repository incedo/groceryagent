package com.groceryautomate.picnic.application.service

import com.groceryautomate.picnic.domain.PicnicDecimal
import com.groceryautomate.picnic.domain.PicnicNutrition
import com.groceryautomate.picnic.domain.PicnicNutritionBasis
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal fun extractNutrition(page: JsonElement, texts: List<String>): PicnicNutrition? {
    val rows = linkedMapOf<String, String>()
    page.allObjects().filter {
        it.string("type") == "STACK" && it.string("axis") == "HORIZONTAL"
    }.forEach { stack ->
        val children = (stack["children"] as? JsonArray).orEmpty()
            .filterIsInstance<JsonObject>()
            .filter { it.string("type") == "RICH_TEXT" }
        if (children.size == 2) {
            val label = children[0].stringOrNull("markdown")?.let(::stripColorMarkup)
            val value = children[1].stringOrNull("markdown")?.let(::stripColorMarkup)
            if (!label.isNullOrBlank() && !value.isNullOrBlank() && label !in rows) rows[label] = value
        }
    }
    val nutritionKeys = setOf("Energie", "kcal", "Koolhydraten", "waarvan suikers", "Vet", "Eiwit", "Zout")
    if (rows.keys.none { it in nutritionKeys }) return null
    return PicnicNutrition(
        basis = when {
            texts.any { it.contains("Per 100 ml", ignoreCase = true) } -> PicnicNutritionBasis.PER_100_MILLILITRES
            texts.any { it.contains("Per 100 g", ignoreCase = true) } -> PicnicNutritionBasis.PER_100_GRAMS
            else -> PicnicNutritionBasis.UNKNOWN
        },
        energyKiloJoules = rows["Energie"]?.digitsOnly()?.toIntOrNull(),
        energyKiloCalories = rows["kcal"]?.firstInteger(),
        carbohydratesGrams = rows["Koolhydraten"].decimalAmount(),
        sugarsGrams = rows["waarvan suikers"].decimalAmount(),
        fatGrams = rows["Vet"].decimalAmount(),
        saturatedFatGrams = rows["waarvan verzadigd"].decimalAmount(),
        proteinGrams = rows["Eiwit"].decimalAmount(),
        saltGrams = rows["Zout"].decimalAmount(),
        fibreGrams = rows["Vezels"].decimalAmount()
    )
}

private fun String?.decimalAmount(): PicnicDecimal? {
    val number = this?.let { Regex("[0-9]+(?:[.,][0-9]+)?").find(it)?.value } ?: return null
    val normalized = number.replace(',', '.')
    val whole = normalized.substringBefore('.')
    val fraction = normalized.substringAfter('.', "")
    return (whole + fraction).toLongOrNull()?.let { PicnicDecimal(it, fraction.length) }
}

private fun String.digitsOnly(): String = substringBefore('/').filter(Char::isDigit)
private fun String.firstInteger(): Int? = Regex("[0-9]+").find(this)?.value?.toIntOrNull()

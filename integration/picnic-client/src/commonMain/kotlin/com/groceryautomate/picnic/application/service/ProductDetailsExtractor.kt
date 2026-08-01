package com.groceryautomate.picnic.application.service

import com.groceryautomate.picnic.domain.PicnicBundleItem
import com.groceryautomate.picnic.domain.PicnicProductDetails
import com.groceryautomate.picnic.domain.PicnicProductInfoSection
import com.groceryautomate.picnic.domain.PicnicProductSummary
import com.groceryautomate.picnic.domain.PicnicProviderSource
import com.groceryautomate.picnic.domain.PicnicSimilarProduct
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal fun extractProductDetails(
    productId: String,
    page: JsonElement,
    source: PicnicProviderSource
): PicnicProductDetails {
    val mainContainer = page.findObjectById("product-details-page-root-main-container")
    val providerProduct = page.objectsNamed("sellingUnit").firstOrNull {
        it.string("id") == productId && it.stringOrNull("name") != null
    } ?: page.allObjects().firstOrNull {
        it.string("id") == productId && it.stringOrNull("name") != null
    }
    val base = providerProduct?.toProductSummary() ?: emptyProduct(productId)
    val texts = orderedProductTexts(page)
    val summary = base.copy(
        name = base.name.ifBlank { mainContainer.headerText().orEmpty() },
        brand = base.brand ?: mainContainer.brandText(),
        priceCents = base.priceCents ?: mainContainer?.firstPositiveInt("price"),
        unitQuantity = base.unitQuantity ?: mainContainer.quantityTexts().getOrNull(0),
        imageId = base.imageId ?: extractImageIds(page).firstOrNull(),
        maxCount = base.maxCount ?: providerProduct?.intOrNull("max_count")
    )
    val sections = extractProductSections(texts)
    return PicnicProductDetails(
        product = summary,
        ingredients = sections["Ingrediënten"],
        allergens = extractAllergens(texts, page),
        nutrition = extractNutrition(page, texts),
        preparation = extractPreparation(texts),
        storage = sections["Bewaren"],
        description = sections["Productomschrijving"] ?: sections["Beschrijving"],
        originCountry = sections["Land van herkomst"],
        supplier = sections["Leverancier"],
        highlights = page.findObjectById("product-page-highlights").cleanMarkdowns(),
        extraInformation = extractExtraInformation(texts),
        infoSections = extractInfoSections(page),
        bundles = extractBundles(page),
        similarProducts = extractSimilarProducts(page, productId),
        source = source
    )
}

private fun emptyProduct(id: String) = PicnicProductSummary(
    id = id,
    name = "",
    brand = null,
    priceCents = null,
    unitQuantity = null,
    imageId = null,
    maxCount = null,
    priceRanges = emptyList(),
    promotion = null
)

private fun JsonObject?.headerText(): String? = this?.allObjects()
    ?.firstOrNull { it.string("textType") == "HEADER1" }
    ?.stringOrNull("markdown")
    ?.let(::stripColorMarkup)

private fun JsonObject?.brandText(): String? = this?.allObjects()
    ?.firstOrNull { node ->
        (node["textAttributes"] as? JsonObject)?.string("weight") == "REGULAR" &&
            node.stringOrNull("markdown") != null
    }
    ?.stringOrNull("markdown")
    ?.let(::stripColorMarkup)

private fun JsonObject?.quantityTexts(): List<String> = this?.allObjects()
    ?.firstOrNull { it.string("type") == "STACK" }
    ?.get("children")
    ?.let { it as? JsonArray }
    .orEmpty()
    .filterIsInstance<JsonObject>()
    .filter { it.string("type") == "RICH_TEXT" }
    .mapNotNull { it.stringOrNull("markdown")?.let(::stripColorMarkup) }

private fun JsonElement.firstPositiveInt(name: String): Int? = valuesNamed(name)
    .filterIsInstance<JsonPrimitive>()
    .mapNotNull(JsonPrimitive::compatibleIntOrNull)
    .firstOrNull { it > 0 }

private fun extractImageIds(page: JsonElement): List<String> {
    val gallery = page.findObjectById("product-page-image-gallery-main-image-container")
    return gallery?.objectsNamed("source")
        ?.mapNotNull { it.stringOrNull("id") }
        ?.distinct()
        ?.toList()
        .orEmpty()
}

private fun extractInfoSections(page: JsonElement): List<PicnicProductInfoSection> {
    val accordion = page.findObjectById("accordion-list") ?: return emptyList()
    val items = accordion.valuesNamed("items").filterIsInstance<JsonArray>().firstOrNull()
        ?: return emptyList()
    return items.filterIsInstance<JsonObject>().map { item ->
        PicnicProductInfoSection(
            item["header"]?.cleanMarkdowns()?.firstOrNull().orEmpty(),
            item["body"]?.markdowns()?.map(::stripColorMarkup).orEmpty().joinToString("\n")
        )
    }
}

private fun extractBundles(page: JsonElement): List<PicnicBundleItem> {
    val container = page.allObjects().firstOrNull { it.string("id").startsWith("product-page-bundles-") }
        ?: return emptyList()
    return container.allObjects().filter {
        it.string("type") == "STATE_BOUNDARY" && it.string("id").startsWith("s")
    }.mapIndexedNotNull { index, boundary ->
        val unit = boundary.objectsNamed("sellingUnit").firstOrNull() ?: return@mapIndexedNotNull null
        PicnicBundleItem(
            id = unit.string("id"),
            quantity = index + 1,
            pricePerUnitCents = boundary.firstPositiveInt("price") ?: 0,
            imageId = unit.string("image_id"),
            maxCount = unit.int("max_count")
        )
    }.toList()
}

private fun extractSimilarProducts(page: JsonElement, productId: String): List<PicnicSimilarProduct> {
    val alternatives = page.findObjectById("alternatives-container") ?: return emptyList()
    return alternatives.objectsNamed("sellingUnit")
        .mapNotNull(JsonObject::toProductSummary)
        .filter { it.id != productId }
        .distinctBy(PicnicProductSummary::id)
        .map { PicnicSimilarProduct(it, null) }
        .toList()
}

private fun JsonElement?.cleanMarkdowns(): List<String> = this?.markdowns().orEmpty()
    .map { stripMarkdown(stripColorMarkup(it)) }

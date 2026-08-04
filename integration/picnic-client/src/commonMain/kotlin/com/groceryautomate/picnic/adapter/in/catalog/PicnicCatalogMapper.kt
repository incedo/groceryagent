package com.groceryautomate.picnic.adapter.`in`.catalog

import com.groceryautomate.catalog.AllergenStatement
import com.groceryautomate.catalog.AvailabilityStatus
import com.groceryautomate.catalog.CatalogProduct
import com.groceryautomate.catalog.DecimalAmount
import com.groceryautomate.catalog.Money
import com.groceryautomate.catalog.Nutrition
import com.groceryautomate.catalog.NutritionBasis
import com.groceryautomate.catalog.PreparationMethod
import com.groceryautomate.catalog.PreparationStep
import com.groceryautomate.catalog.Product
import com.groceryautomate.catalog.ProductComposition
import com.groceryautomate.catalog.ProductId
import com.groceryautomate.catalog.ProductOffer
import com.groceryautomate.catalog.ProductOfferId
import com.groceryautomate.catalog.ProductSearchResult
import com.groceryautomate.catalog.Promotion
import com.groceryautomate.catalog.ProviderEvidence
import com.groceryautomate.catalog.ProviderRouteGeneration
import com.groceryautomate.catalog.RetailerId
import com.groceryautomate.catalog.TierPrice
import com.groceryautomate.catalog.VerificationStatus
import com.groceryautomate.catalog.parsePackageQuantity
import com.groceryautomate.picnic.domain.PicnicAllergenDataStatus
import com.groceryautomate.picnic.domain.PicnicDecimal
import com.groceryautomate.picnic.domain.PicnicNutrition
import com.groceryautomate.picnic.domain.PicnicNutritionBasis
import com.groceryautomate.picnic.domain.PicnicProductDetails
import com.groceryautomate.picnic.domain.PicnicProductSummary
import com.groceryautomate.picnic.domain.PicnicProviderSource
import com.groceryautomate.picnic.domain.PicnicRouteGeneration
import com.groceryautomate.picnic.domain.PicnicSearchResult

internal fun PicnicSearchResult.toCanonical(limit: Int): ProductSearchResult = ProductSearchResult(
    query = query,
    totalProviderCount = products.size,
    products = products.take(limit).map { it.toCanonical(source, null) }
)

internal fun PicnicProductDetails.toCanonical(): CatalogProduct = product.toCanonical(
    source = source,
    composition = ProductComposition(
        ingredients = ingredients,
        allergens = AllergenStatement(
            contains = allergens.contains,
            mayContain = allergens.mayContain,
            status = allergens.status.toCanonical()
        ),
        nutrition = nutrition?.toCanonical(),
        preparation = preparation.map { method ->
            PreparationMethod(
                method.method,
                method.steps.map { PreparationStep(it.number, it.text) }
            )
        },
        storage = storage,
        originCountry = originCountry,
        supplier = supplier,
        additionalInformation = buildMap {
            putAll(extraInformation)
            infoSections.forEach { section ->
                if (section.title !in this) put(section.title, section.content)
            }
        }
    ),
    description = description,
    highlights = highlights
)

private fun PicnicProductSummary.toCanonical(
    source: PicnicProviderSource,
    composition: ProductComposition?,
    description: String? = null,
    highlights: List<String> = emptyList()
): CatalogProduct {
    val canonicalId = ProductId("picnic:${source.countryCode}:$id")
    val evidence = source.toEvidence(id)
    return CatalogProduct(
        product = Product(
            id = canonicalId,
            name = name,
            brand = brand,
            description = description,
            imageId = imageId,
            highlights = highlights
        ),
        composition = composition,
        offers = toOffer(canonicalId, source, evidence)?.let(::listOf).orEmpty(),
        evidence = evidence
    )
}

private fun PicnicProductSummary.toOffer(
    productId: ProductId,
    source: PicnicProviderSource,
    evidence: ProviderEvidence
): ProductOffer? {
    val currentPrice = promotion?.priceCents ?: priceCents ?: return null
    return ProductOffer(
        id = ProductOfferId("picnic:${source.countryCode}:$id:current"),
        productId = productId,
        retailerId = RetailerId("picnic"),
        region = source.countryCode,
        price = currentPrice.euros(),
        packageQuantity = parsePackageQuantity(unitQuantity),
        tierPrices = priceRanges.map { TierPrice(it.fromQuantity, it.priceCents.euros()) },
        promotion = promotion?.let {
            Promotion(
                id = it.id,
                label = it.label,
                originalPrice = it.strikethroughPriceCents?.euros(),
                badgeText = it.badgeText
            )
        },
        availability = AvailabilityStatus.UNKNOWN,
        evidence = evidence
    )
}

private fun PicnicProviderSource.toEvidence(externalId: String) = ProviderEvidence(
    provider = provider,
    externalId = externalId,
    endpoint = endpoint,
    region = countryCode,
    observedAt = observedAt,
    apiVersion = apiVersion,
    routeGeneration = when (routeGeneration) {
        PicnicRouteGeneration.CURRENT -> ProviderRouteGeneration.CURRENT
        PicnicRouteGeneration.LEGACY -> ProviderRouteGeneration.LEGACY
    }
)

private fun PicnicAllergenDataStatus.toCanonical(): VerificationStatus = when (this) {
    PicnicAllergenDataStatus.OBSERVED -> VerificationStatus.OBSERVED
    PicnicAllergenDataStatus.UNKNOWN -> VerificationStatus.UNKNOWN
}

private fun PicnicNutrition.toCanonical() = Nutrition(
    basis = when (basis) {
        PicnicNutritionBasis.PER_100_GRAMS -> NutritionBasis.PER_100_GRAMS
        PicnicNutritionBasis.PER_100_MILLILITRES -> NutritionBasis.PER_100_MILLILITRES
        PicnicNutritionBasis.UNKNOWN -> NutritionBasis.UNKNOWN
    },
    energyKiloJoules = energyKiloJoules,
    energyKiloCalories = energyKiloCalories,
    carbohydratesGrams = carbohydratesGrams?.toCanonical(),
    sugarsGrams = sugarsGrams?.toCanonical(),
    fatGrams = fatGrams?.toCanonical(),
    saturatedFatGrams = saturatedFatGrams?.toCanonical(),
    proteinGrams = proteinGrams?.toCanonical(),
    saltGrams = saltGrams?.toCanonical(),
    fibreGrams = fibreGrams?.toCanonical()
)

private fun PicnicDecimal.toCanonical() = DecimalAmount(unscaledValue, scale)
private fun Int.euros() = Money(toLong(), "EUR")

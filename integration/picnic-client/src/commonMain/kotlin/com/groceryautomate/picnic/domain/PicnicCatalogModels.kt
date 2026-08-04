package com.groceryautomate.picnic.domain

data class PicnicProviderSource(
    val provider: String = "picnic",
    val endpoint: String,
    val countryCode: String,
    val apiVersion: Int,
    val observedAt: String,
    val routeGeneration: PicnicRouteGeneration = PicnicRouteGeneration.CURRENT
)

data class PicnicSearchRequest(
    val query: String,
    val sessionId: String? = null,
    val pendingSessionId: String? = null,
    val recommendationsActive: Boolean = false,
    val textInputFocused: Boolean = false
)

data class PicnicSearchResult(
    val query: String,
    val products: List<PicnicProductSummary>,
    val source: PicnicProviderSource
)

data class PicnicPriceRange(
    val priceCents: Int,
    val fromQuantity: Int
)

data class PicnicPromotion(
    val id: String?,
    val label: String?,
    val priceCents: Int?,
    val strikethroughPriceCents: Int?,
    val showStrikethroughPrice: Boolean,
    val badgeText: String?
)

data class PicnicProductSummary(
    val id: String,
    val name: String,
    val brand: String?,
    val priceCents: Int?,
    val unitQuantity: String?,
    val imageId: String?,
    val maxCount: Int?,
    val priceRanges: List<PicnicPriceRange>,
    val promotion: PicnicPromotion?
)

enum class PicnicAllergenDataStatus {
    OBSERVED,
    UNKNOWN
}

data class PicnicAllergenStatement(
    val contains: List<String>,
    val mayContain: List<String>,
    val status: PicnicAllergenDataStatus
)

data class PicnicDecimal(
    val unscaledValue: Long,
    val scale: Int
) {
    init {
        require(scale >= 0) { "Decimal scale must not be negative." }
    }
}

enum class PicnicNutritionBasis {
    PER_100_GRAMS,
    PER_100_MILLILITRES,
    UNKNOWN
}

data class PicnicNutrition(
    val basis: PicnicNutritionBasis,
    val energyKiloJoules: Int?,
    val energyKiloCalories: Int?,
    val carbohydratesGrams: PicnicDecimal?,
    val sugarsGrams: PicnicDecimal?,
    val fatGrams: PicnicDecimal?,
    val saturatedFatGrams: PicnicDecimal?,
    val proteinGrams: PicnicDecimal?,
    val saltGrams: PicnicDecimal?,
    val fibreGrams: PicnicDecimal?
)

data class PicnicPreparationStep(
    val number: Int,
    val text: String
)

data class PicnicPreparationMethod(
    val method: String?,
    val steps: List<PicnicPreparationStep>
)

data class PicnicProductInfoSection(
    val title: String,
    val content: String
)

data class PicnicBundleItem(
    val id: String,
    val quantity: Int,
    val pricePerUnitCents: Int,
    val imageId: String,
    val maxCount: Int
)

data class PicnicSimilarProduct(
    val product: PicnicProductSummary,
    val depositCents: Int?
)

data class PicnicProductDetails(
    val product: PicnicProductSummary,
    val ingredients: String?,
    val allergens: PicnicAllergenStatement,
    val nutrition: PicnicNutrition?,
    val preparation: List<PicnicPreparationMethod>,
    val storage: String?,
    val description: String?,
    val originCountry: String?,
    val supplier: String?,
    val highlights: List<String>,
    val extraInformation: Map<String, String>,
    val infoSections: List<PicnicProductInfoSection>,
    val bundles: List<PicnicBundleItem>,
    val similarProducts: List<PicnicSimilarProduct>,
    val source: PicnicProviderSource
)

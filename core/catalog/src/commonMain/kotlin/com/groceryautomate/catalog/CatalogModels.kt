package com.groceryautomate.catalog

import kotlinx.serialization.Serializable

@Serializable
data class ProviderEvidence(
    val provider: String,
    val externalId: String,
    val endpoint: String,
    val region: String,
    val observedAt: String,
    val apiVersion: Int,
    val routeGeneration: ProviderRouteGeneration,
    val verification: VerificationStatus = VerificationStatus.OBSERVED
) {
    init {
        require(provider.isNotBlank()) { "Evidence provider must not be blank." }
        require(externalId.isNotBlank()) { "Evidence external id must not be blank." }
        require(endpoint.isNotBlank()) { "Evidence endpoint must not be blank." }
        require(region.isNotBlank()) { "Evidence region must not be blank." }
        require(observedAt.isNotBlank()) { "Evidence observation time must not be blank." }
        require(apiVersion > 0) { "Evidence API version must be positive." }
    }
}

@Serializable
enum class ProviderRouteGeneration {
    CURRENT,
    LEGACY
}

@Serializable
enum class VerificationStatus {
    OBSERVED,
    UNKNOWN
}

@Serializable
data class Product(
    val id: ProductId,
    val name: String,
    val brand: String?,
    val description: String?,
    val imageId: String?,
    val highlights: List<String> = emptyList()
) {
    init {
        require(name.isNotBlank()) { "Product name must not be blank." }
    }
}

@Serializable
data class AllergenStatement(
    val contains: List<String>,
    val mayContain: List<String>,
    val status: VerificationStatus
)

@Serializable
enum class NutritionBasis {
    PER_100_GRAMS,
    PER_100_MILLILITRES,
    UNKNOWN
}

@Serializable
data class Nutrition(
    val basis: NutritionBasis,
    val energyKiloJoules: Int?,
    val energyKiloCalories: Int?,
    val carbohydratesGrams: DecimalAmount?,
    val sugarsGrams: DecimalAmount?,
    val fatGrams: DecimalAmount?,
    val saturatedFatGrams: DecimalAmount?,
    val proteinGrams: DecimalAmount?,
    val saltGrams: DecimalAmount?,
    val fibreGrams: DecimalAmount?
)

@Serializable
data class PreparationStep(
    val number: Int,
    val text: String
)

@Serializable
data class PreparationMethod(
    val method: String?,
    val steps: List<PreparationStep>
)

@Serializable
data class ProductComposition(
    val ingredients: String?,
    val allergens: AllergenStatement,
    val nutrition: Nutrition?,
    val preparation: List<PreparationMethod>,
    val storage: String?,
    val originCountry: String?,
    val supplier: String?,
    val additionalInformation: Map<String, String>
)

@Serializable
data class Promotion(
    val id: String?,
    val label: String?,
    val originalPrice: Money?,
    val badgeText: String?
)

@Serializable
data class TierPrice(
    val minimumQuantity: Int,
    val unitPrice: Money
) {
    init {
        require(minimumQuantity > 0) { "Tier minimum quantity must be positive." }
    }
}

@Serializable
enum class AvailabilityStatus {
    AVAILABLE,
    UNAVAILABLE,
    UNKNOWN
}

@Serializable
data class ProductOffer(
    val id: ProductOfferId,
    val productId: ProductId,
    val retailerId: RetailerId,
    val region: String,
    val price: Money,
    val packageQuantity: PackageQuantity?,
    val tierPrices: List<TierPrice>,
    val promotion: Promotion?,
    val availability: AvailabilityStatus,
    val evidence: ProviderEvidence
)

@Serializable
data class CatalogProduct(
    val product: Product,
    val composition: ProductComposition?,
    val offers: List<ProductOffer>,
    val evidence: ProviderEvidence
)

@Serializable
data class ProductSearchResult(
    val query: String,
    val totalProviderCount: Int,
    val products: List<CatalogProduct>
)

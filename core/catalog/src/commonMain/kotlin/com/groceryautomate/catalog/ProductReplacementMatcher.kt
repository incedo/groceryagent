package com.groceryautomate.catalog

data class HistoricalProductReference(
    val previousProductId: ProductId,
    val name: String,
    val unitQuantity: String
) {
    init {
        require(name.isNotBlank()) { "Historical product name must not be blank." }
        require(unitQuantity.isNotBlank()) { "Historical unit quantity must not be blank." }
    }
}

sealed interface ProductReplacementMatch {
    data class Matched(val product: CatalogProduct) : ProductReplacementMatch
    data object NoMatch : ProductReplacementMatch
    data class Ambiguous(val productIds: List<ProductId>) : ProductReplacementMatch
    data object SameId : ProductReplacementMatch
}

object ProductReplacementMatcher {
    fun match(
        reference: HistoricalProductReference,
        result: ProductSearchResult
    ): ProductReplacementMatch {
        val expectedPackage = parsePackageQuantity(reference.unitQuantity)
        val candidates = result.products
            .filter { normalizeName(it.product.name) == normalizeName(reference.name) }
            .filter { candidate ->
                candidate.offers.any { offer ->
                    packagesMatch(expectedPackage, offer.packageQuantity)
                }
            }
            .distinctBy { it.product.id }
        return when {
            candidates.isEmpty() -> ProductReplacementMatch.NoMatch
            candidates.size > 1 -> ProductReplacementMatch.Ambiguous(
                candidates.map { it.product.id }.sortedBy(ProductId::value)
            )
            candidates.single().product.id == reference.previousProductId -> ProductReplacementMatch.SameId
            else -> ProductReplacementMatch.Matched(candidates.single())
        }
    }
}

private fun normalizeName(value: String): String = value.trim().lowercase()
    .replace(Regex("\\s+"), " ")

private fun packagesMatch(expected: PackageQuantity?, actual: PackageQuantity?): Boolean {
    if (expected == null || actual == null) return false
    if (expected.unit == QuantityUnit.UNKNOWN || actual.unit == QuantityUnit.UNKNOWN) {
        return normalizePackageText(expected.originalText) == normalizePackageText(actual.originalText)
    }
    if (expected.unit.dimension != actual.unit.dimension) return false
    val expectedAmount = expected.amount?.toBaseAmount(expected.unit, expected.packageCount) ?: return false
    val actualAmount = actual.amount?.toBaseAmount(actual.unit, actual.packageCount) ?: return false
    return expectedAmount.isEqualTo(actualAmount)
}

private fun normalizePackageText(value: String): String = value.trim().lowercase()
    .replace(Regex("\\s+"), " ")

private data class ScaledAmount(val unscaled: Long, val scale: Int) {
    fun isEqualTo(other: ScaledAmount): Boolean {
        val targetScale = maxOf(scale, other.scale)
        return unscaled * tenPower(targetScale - scale) ==
            other.unscaled * tenPower(targetScale - other.scale)
    }
}

private fun DecimalAmount.toBaseAmount(unit: QuantityUnit, packageCount: Int): ScaledAmount {
    val unitFactor = when (unit) {
        QuantityUnit.KILOGRAM, QuantityUnit.LITRE -> 1_000L
        else -> 1L
    }
    return ScaledAmount(unscaledValue * unitFactor * packageCount, scale)
}

private fun tenPower(exponent: Int): Long {
    var result = 1L
    repeat(exponent) { result *= 10L }
    return result
}

package com.groceryautomate.catalog

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class HistoricalPriceObservationId(val value: String) {
    init {
        require(value.isNotBlank()) { "Historical price observation id must not be blank." }
    }
}

@Serializable
data class HistoricalPriceObservation(
    val id: HistoricalPriceObservationId,
    val productId: ProductId,
    val retailerId: RetailerId,
    val region: String,
    val paidLineTotal: Money,
    val originalLineTotal: Money?,
    val quantity: Int,
    val packageText: String?,
    val promotionLabel: String?,
    val purchasedAt: String,
    val source: String
) {
    init {
        require(region.isNotBlank()) { "Historical price region must not be blank." }
        require(quantity > 0) { "Historical price quantity must be positive." }
        require(purchasedAt.isNotBlank()) { "Historical price purchase time must not be blank." }
        require(source.isNotBlank()) { "Historical price source must not be blank." }
        require(originalLineTotal == null || originalLineTotal.currency == paidLineTotal.currency) {
            "Historical paid and original totals must use the same currency."
        }
    }
}

@Serializable
data class ProductPriceHistory(
    val productId: ProductId,
    val observations: List<HistoricalPriceObservation>
)

interface PriceHistoryPort {
    suspend fun getPriceHistory(productId: ProductId, limit: Int = 100): ProductPriceHistory
}

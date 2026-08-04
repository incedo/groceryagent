package com.groceryautomate.catalog

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class ProductId(val value: String) {
    init {
        require(value.isNotBlank()) { "Product id must not be blank." }
    }
}

@Serializable
@JvmInline
value class ProductOfferId(val value: String) {
    init {
        require(value.isNotBlank()) { "Product offer id must not be blank." }
    }
}

@Serializable
@JvmInline
value class RetailerId(val value: String) {
    init {
        require(value.isNotBlank()) { "Retailer id must not be blank." }
    }
}

@Serializable
data class DecimalAmount(
    val unscaledValue: Long,
    val scale: Int
) {
    init {
        require(scale >= 0) { "Decimal scale must not be negative." }
    }
}

@Serializable
data class Money(
    val minorUnits: Long,
    val currency: String
) {
    init {
        require(minorUnits >= 0) { "Money must not be negative." }
        require(currency.matches(Regex("[A-Z]{3}"))) { "Currency must be an ISO 4217 code." }
    }
}

@Serializable
enum class QuantityDimension {
    MASS,
    VOLUME,
    COUNT,
    UNKNOWN
}

@Serializable
enum class QuantityUnit(val dimension: QuantityDimension) {
    GRAM(QuantityDimension.MASS),
    KILOGRAM(QuantityDimension.MASS),
    MILLILITRE(QuantityDimension.VOLUME),
    LITRE(QuantityDimension.VOLUME),
    ITEM(QuantityDimension.COUNT),
    UNKNOWN(QuantityDimension.UNKNOWN)
}

@Serializable
data class PackageQuantity(
    val amount: DecimalAmount?,
    val unit: QuantityUnit,
    val packageCount: Int = 1,
    val originalText: String
) {
    init {
        require(packageCount > 0) { "Package count must be positive." }
        require(originalText.isNotBlank()) { "Original package quantity must not be blank." }
    }
}

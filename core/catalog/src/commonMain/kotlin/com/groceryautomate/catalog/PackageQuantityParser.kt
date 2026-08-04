package com.groceryautomate.catalog

private val quantityPattern = Regex(
    "^(?:(\\d+)\\s*[x×]\\s*)?([0-9]+(?:[.,][0-9]+)?)\\s*([\\p{L}]+)$",
    RegexOption.IGNORE_CASE
)

fun parsePackageQuantity(value: String?): PackageQuantity? {
    val original = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val match = quantityPattern.matchEntire(original)
        ?: return PackageQuantity(null, QuantityUnit.UNKNOWN, originalText = original)
    val packageCount = match.groupValues[1].toIntOrNull() ?: 1
    val amount = match.groupValues[2].toDecimalAmount()
    val unit = match.groupValues[3].toQuantityUnit()
    return PackageQuantity(amount, unit, packageCount, original)
}

private fun String.toDecimalAmount(): DecimalAmount {
    val normalized = replace(',', '.')
    val whole = normalized.substringBefore('.')
    val fraction = normalized.substringAfter('.', "")
    return DecimalAmount((whole + fraction).toLong(), fraction.length)
}

private fun String.toQuantityUnit(): QuantityUnit = when (lowercase()) {
    "g", "gr", "gram", "grammen" -> QuantityUnit.GRAM
    "kg", "kilo", "kilogram" -> QuantityUnit.KILOGRAM
    "ml", "milliliter", "millilitre" -> QuantityUnit.MILLILITRE
    "l", "liter", "litre" -> QuantityUnit.LITRE
    "stuk", "stuks", "item", "items" -> QuantityUnit.ITEM
    else -> QuantityUnit.UNKNOWN
}

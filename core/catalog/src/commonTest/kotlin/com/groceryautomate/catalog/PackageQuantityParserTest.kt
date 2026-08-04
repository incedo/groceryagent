package com.groceryautomate.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PackageQuantityParserTest {
    @Test
    fun parsesMassWithoutFloatingPoint() {
        val quantity = parsePackageQuantity("1,5 kilo")

        assertEquals(DecimalAmount(15, 1), quantity?.amount)
        assertEquals(QuantityUnit.KILOGRAM, quantity?.unit)
        assertEquals(QuantityDimension.MASS, quantity?.unit?.dimension)
        assertEquals(1, quantity?.packageCount)
    }

    @Test
    fun parsesMultipackVolume() {
        val quantity = parsePackageQuantity("6 x 330 ml")

        assertEquals(DecimalAmount(330, 0), quantity?.amount)
        assertEquals(QuantityUnit.MILLILITRE, quantity?.unit)
        assertEquals(6, quantity?.packageCount)
    }

    @Test
    fun preservesUnsupportedLabelsAsUnknown() {
        val quantity = parsePackageQuantity("voordeelverpakking")

        assertNull(quantity?.amount)
        assertEquals(QuantityUnit.UNKNOWN, quantity?.unit)
        assertEquals("voordeelverpakking", quantity?.originalText)
    }

    @Test
    fun missingLabelRemainsMissing() {
        assertNull(parsePackageQuantity(null))
        assertNull(parsePackageQuantity("  "))
    }
}

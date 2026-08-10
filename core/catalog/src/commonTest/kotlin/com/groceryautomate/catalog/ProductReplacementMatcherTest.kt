package com.groceryautomate.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProductReplacementMatcherTest {
    @Test
    fun matchesExactNormalizedNameAndEquivalentPackage() {
        val result = ProductReplacementMatcher.match(
            HistoricalProductReference(ProductId("picnic:nl:s-old"), "  Kiphaasjes ", "0,3 kg"),
            search(product("picnic:nl:s-new", "kiphaasjes", "300 gram"))
        )

        assertEquals(ProductId("picnic:nl:s-new"), assertIs<ProductReplacementMatch.Matched>(result).product.product.id)
    }

    @Test
    fun rejectsPackageMismatchAndSameId() {
        val reference = HistoricalProductReference(ProductId("picnic:nl:s-old"), "Kiphaasjes", "300 gram")

        assertEquals(
            ProductReplacementMatch.NoMatch,
            ProductReplacementMatcher.match(reference, search(product("picnic:nl:s-new", "Kiphaasjes", "250 gram")))
        )
        assertEquals(
            ProductReplacementMatch.SameId,
            ProductReplacementMatcher.match(reference, search(product("picnic:nl:s-old", "Kiphaasjes", "300 gram")))
        )
    }

    @Test
    fun rejectsAmbiguousExactCandidates() {
        val result = ProductReplacementMatcher.match(
            HistoricalProductReference(ProductId("picnic:nl:s-old"), "Kiphaasjes", "300 gram"),
            search(
                product("picnic:nl:s-two", "Kiphaasjes", "300 gram"),
                product("picnic:nl:s-one", "Kiphaasjes", "300 gram")
            )
        )

        assertEquals(
            listOf(ProductId("picnic:nl:s-one"), ProductId("picnic:nl:s-two")),
            assertIs<ProductReplacementMatch.Ambiguous>(result).productIds
        )
    }
}

private fun search(vararg products: CatalogProduct) = ProductSearchResult(
    "Kiphaasjes",
    products.size,
    products.toList()
)

private fun product(id: String, name: String, unit: String): CatalogProduct {
    val productId = ProductId(id)
    val evidence = ProviderEvidence(
        "picnic",
        id.substringAfterLast(':'),
        "/pages/search-page-root-content",
        "nl",
        "2026-08-10T00:00:00Z",
        15,
        ProviderRouteGeneration.CURRENT
    )
    return CatalogProduct(
        Product(productId, name, null, null, null),
        null,
        listOf(
            ProductOffer(
                ProductOfferId("$id:current"),
                productId,
                RetailerId("picnic"),
                "nl",
                Money(399, "EUR"),
                parsePackageQuantity(unit),
                emptyList(),
                null,
                AvailabilityStatus.UNKNOWN,
                evidence
            )
        ),
        evidence
    )
}

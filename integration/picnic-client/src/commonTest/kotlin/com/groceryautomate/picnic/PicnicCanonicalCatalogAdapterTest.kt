package com.groceryautomate.picnic

import com.groceryautomate.catalog.AvailabilityStatus
import com.groceryautomate.catalog.DecimalAmount
import com.groceryautomate.catalog.NutritionBasis
import com.groceryautomate.catalog.ProductId
import com.groceryautomate.catalog.ProviderRouteGeneration
import com.groceryautomate.catalog.QuantityUnit
import com.groceryautomate.catalog.VerificationStatus
import com.groceryautomate.picnic.adapter.`in`.catalog.PicnicCanonicalCatalogAdapter
import com.groceryautomate.picnic.adapter.out.memory.InMemoryPicnicAuthStore
import com.groceryautomate.picnic.application.port.out.PicnicClock
import com.groceryautomate.picnic.application.port.out.PicnicIdGenerator
import com.groceryautomate.picnic.domain.PicnicAllergenDataStatus
import com.groceryautomate.picnic.domain.PicnicAllergenStatement
import com.groceryautomate.picnic.domain.PicnicCompatibilityException
import com.groceryautomate.picnic.domain.PicnicClientConfig
import com.groceryautomate.picnic.domain.PicnicDecimal
import com.groceryautomate.picnic.domain.PicnicFailureReason
import com.groceryautomate.picnic.domain.PicnicNutrition
import com.groceryautomate.picnic.domain.PicnicNutritionBasis
import com.groceryautomate.picnic.domain.PicnicPreparationMethod
import com.groceryautomate.picnic.domain.PicnicPreparationStep
import com.groceryautomate.picnic.domain.PicnicPriceRange
import com.groceryautomate.picnic.domain.PicnicProductDetails
import com.groceryautomate.picnic.domain.PicnicProductSummary
import com.groceryautomate.picnic.domain.PicnicProductInfoSection
import com.groceryautomate.picnic.domain.PicnicPromotion
import com.groceryautomate.picnic.domain.PicnicProviderSource
import com.groceryautomate.picnic.domain.PicnicRouteAttempt
import com.groceryautomate.picnic.domain.PicnicRouteGeneration
import com.groceryautomate.picnic.domain.PicnicSearchResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PicnicCanonicalCatalogAdapterTest {
    @Test
    fun searchMapsProductsOffersPromotionQuantityAndEvidence() = runTest {
        val source = source(PicnicRouteGeneration.LEGACY)
        val adapter = adapter(searchResult = PicnicSearchResult("oats", listOf(summary()), source))

        val result = adapter.search("oats", 1)
        val product = result.products.single()
        val offer = product.offers.single()

        assertEquals(1, result.totalProviderCount)
        assertEquals(ProductId("picnic:nl:s1001"), product.product.id)
        assertNull(product.composition)
        assertEquals(179, offer.price.minorUnits)
        assertEquals("EUR", offer.price.currency)
        assertEquals(QuantityUnit.GRAM, offer.packageQuantity?.unit)
        assertEquals(DecimalAmount(500, 0), offer.packageQuantity?.amount)
        assertEquals(199, offer.promotion?.originalPrice?.minorUnits)
        assertEquals(2, offer.tierPrices.single().minimumQuantity)
        assertEquals(AvailabilityStatus.UNKNOWN, offer.availability)
        assertEquals(ProviderRouteGeneration.LEGACY, product.evidence.routeGeneration)
        assertEquals("2026-08-04T10:00:00Z", offer.evidence.observedAt)
    }

    @Test
    fun detailMapsCompositionAndPassesCanonicalIdAsExternalId() = runTest {
        var requestedId: String? = null
        val adapter = PicnicCanonicalCatalogAdapter(
            search = { PicnicSearchResult(it, emptyList(), source()) },
            getProduct = {
                requestedId = it
                details()
            }
        )

        val product = adapter.getProduct(ProductId("picnic:nl:s1001"))!!
        val composition = product.composition!!

        assertEquals("s1001", requestedId)
        assertEquals("Wholegrain oats", composition.ingredients)
        assertEquals(listOf("gluten"), composition.allergens.contains)
        assertEquals(VerificationStatus.OBSERVED, composition.allergens.status)
        assertEquals(NutritionBasis.PER_100_GRAMS, composition.nutrition?.basis)
        assertEquals(DecimalAmount(132, 1), composition.nutrition?.proteinGrams)
        assertEquals("Netherlands", composition.originCountry)
        assertEquals("Keep dry", composition.storage)
        assertEquals("Organic", product.product.highlights.single())
    }

    @Test
    fun missingPriceCreatesNoOfferAndUnknownAllergensStayUnknown() = runTest {
        val product = details(
            product = summary().copy(priceCents = null, promotion = null),
            allergens = PicnicAllergenStatement(emptyList(), emptyList(), PicnicAllergenDataStatus.UNKNOWN)
        )
        val adapter = adapter(productDetails = product)

        val result = adapter.getProduct(ProductId("s1001"))!!

        assertEquals(emptyList(), result.offers)
        assertEquals(VerificationStatus.UNKNOWN, result.composition?.allergens?.status)
    }

    @Test
    fun mapsPreparationInfoSectionsAndVolumeNutrition() = runTest {
        val enriched = details().copy(
            nutrition = details().nutrition?.copy(basis = PicnicNutritionBasis.PER_100_MILLILITRES),
            preparation = listOf(
                PicnicPreparationMethod("Cook", listOf(PicnicPreparationStep(1, "Add water")))
            ),
            infoSections = listOf(PicnicProductInfoSection("Recycling", "Paper packaging"))
        )

        val product = adapter(productDetails = enriched).getProduct(ProductId("s1001"))!!

        assertEquals(NutritionBasis.PER_100_MILLILITRES, product.composition?.nutrition?.basis)
        assertEquals("Add water", product.composition?.preparation?.single()?.steps?.single()?.text)
        assertEquals("Paper packaging", product.composition?.additionalInformation?.get("Recycling"))
    }

    @Test
    fun publicAdapterConstructorMapsThroughRealPicnicCatalogPort() = runTest {
        val transport = RecordingTransport {
            jsonResponse(
                """{"sellingUnit":{"id":"s2002","name":"Brown rice","price":249,"unit_quantity":"1 kilo"}}"""
            )
        }
        val picnic = PicnicClient(
            config = PicnicClientConfig(baseUrlOverride = "https://picnic.example.test/api/15"),
            transport = transport,
            authStore = InMemoryPicnicAuthStore("fixture-token"),
            clock = PicnicClock { "2026-08-04T11:00:00Z" },
            idGenerator = PicnicIdGenerator { "11111111-1111-4111-8111-111111111111" }
        )
        val adapter = PicnicCanonicalCatalogAdapter(picnic.catalog)

        val search = adapter.search("rice", 10)
        val detail = adapter.getProduct(ProductId("picnic:nl:s2002"))

        assertEquals("Brown rice", search.products.single().product.name)
        assertEquals("Brown rice", detail?.product?.name)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun routeAbsenceIsNotFoundButMixedCompatibilityFailureIsNot() = runTest {
        val missing = PicnicCompatibilityException(
            listOf(
                PicnicRouteAttempt(PicnicRouteGeneration.CURRENT, 404, PicnicFailureReason.ROUTE_UNAVAILABLE),
                PicnicRouteAttempt(PicnicRouteGeneration.LEGACY, 410, PicnicFailureReason.ROUTE_UNAVAILABLE)
            )
        )
        val mixed = PicnicCompatibilityException(
            listOf(
                PicnicRouteAttempt(PicnicRouteGeneration.CURRENT, null, PicnicFailureReason.MAPPING_INCOMPATIBLE),
                PicnicRouteAttempt(PicnicRouteGeneration.LEGACY, 404, PicnicFailureReason.ROUTE_UNAVAILABLE)
            )
        )

        assertNull(adapter(productFailure = missing).getProduct(ProductId("s404")))
        assertFailsWith<PicnicCompatibilityException> {
            adapter(productFailure = mixed).getProduct(ProductId("s500"))
        }
    }

    private fun adapter(
        searchResult: PicnicSearchResult = PicnicSearchResult("oats", listOf(summary()), source()),
        productDetails: PicnicProductDetails = details(),
        productFailure: Throwable? = null
    ) = PicnicCanonicalCatalogAdapter(
        search = { searchResult },
        getProduct = { productFailure?.let { throw it } ?: productDetails }
    )
}

private fun source(generation: PicnicRouteGeneration = PicnicRouteGeneration.CURRENT) =
    PicnicProviderSource(
        endpoint = "/pages/product-details-page-root",
        countryCode = "nl",
        apiVersion = 15,
        observedAt = "2026-08-04T10:00:00Z",
        routeGeneration = generation
    )

private fun summary() = PicnicProductSummary(
    id = "s1001",
    name = "Wholegrain oats",
    brand = "Picnic",
    priceCents = 189,
    unitQuantity = "500 gram",
    imageId = "image-1",
    maxCount = 20,
    priceRanges = listOf(PicnicPriceRange(169, 2)),
    promotion = PicnicPromotion("promo-1", "Actie", 179, 199, true, "Korting")
)

private fun details(
    product: PicnicProductSummary = summary(),
    allergens: PicnicAllergenStatement = PicnicAllergenStatement(
        listOf("gluten"),
        listOf("nuts"),
        PicnicAllergenDataStatus.OBSERVED
    )
) = PicnicProductDetails(
    product = product,
    ingredients = "Wholegrain oats",
    allergens = allergens,
    nutrition = PicnicNutrition(
        PicnicNutritionBasis.PER_100_GRAMS,
        1550,
        370,
        PicnicDecimal(60, 0),
        PicnicDecimal(10, 0),
        PicnicDecimal(70, 1),
        PicnicDecimal(10, 1),
        PicnicDecimal(132, 1),
        PicnicDecimal(1, 1),
        PicnicDecimal(100, 1)
    ),
    preparation = emptyList(),
    storage = "Keep dry",
    description = "Organic wholegrain oats",
    originCountry = "Netherlands",
    supplier = "Fixture supplier",
    highlights = listOf("Organic"),
    extraInformation = mapOf("Certification" to "EU organic"),
    infoSections = emptyList(),
    bundles = emptyList(),
    similarProducts = emptyList(),
    source = source()
)

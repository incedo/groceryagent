package com.groceryautomate.picnic

import com.groceryautomate.picnic.adapter.out.memory.InMemoryPicnicAuthStore
import com.groceryautomate.picnic.application.port.out.PicnicClock
import com.groceryautomate.picnic.application.port.out.PicnicIdGenerator
import com.groceryautomate.picnic.domain.PicnicAllergenDataStatus
import com.groceryautomate.picnic.domain.PicnicClientConfig
import com.groceryautomate.picnic.domain.PicnicDecimal
import com.groceryautomate.picnic.domain.PicnicImageSize
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProductDetailsContractTest {
    @Test
    fun currentPageShapesBecomeTypedCatalogObjects() = runTest {
        val transport = RecordingTransport { request ->
            when {
                "search-page-root-content" in request.url -> jsonResponse(searchFixture)
                "product-details-page-root" in request.url -> jsonResponse(detailsFixture)
                "/static/images/" in request.url -> binaryResponse(byteArrayOf(1, 2, 3))
                else -> jsonResponse("{}")
            }
        }
        val client = PicnicClient(
            PicnicClientConfig(baseUrlOverride = "https://picnic.example.test/api/15"),
            transport,
            InMemoryPicnicAuthStore("token"),
            PicnicClock { "2026-08-01T10:15:30Z" },
            idGenerator = PicnicIdGenerator { "11111111-1111-4111-8111-111111111111" }
        )

        val search = client.catalog.search("half vol")
        val details = client.catalog.getProductDetails("s1")
        val image = client.catalog.getImageAsDataUri("img", PicnicImageSize.SMALL)

        assertEquals("s1", search.products.single().id)
        assertEquals(189, search.products.single().priceCents)
        assertEquals(259, search.products.single().promotion?.strikethroughPriceCents)
        assertTrue(transport.requests.first().url.contains("/pages/search-page-root-content?"))
        assertTrue(transport.requests.first().url.contains("search_term=half%20vol"))
        assertTrue(transport.requests.first().url.contains("search_session_id=11111111"))
        assertEquals("2026-08-01T10:15:30Z", search.source.observedAt)

        assertEquals("Milk", details.product.name)
        assertEquals("Dairy", details.product.brand)
        assertEquals(189, details.product.priceCents)
        assertEquals(listOf("Milk"), details.allergens.contains)
        assertEquals(listOf("Nuts"), details.allergens.mayContain)
        assertEquals(PicnicAllergenDataStatus.OBSERVED, details.allergens.status)
        assertEquals(PicnicDecimal(48, 1), details.nutrition?.carbohydratesGrams)
        assertEquals("Keep refrigerated", details.storage)
        assertEquals("Netherlands", details.originCountry)
        assertEquals(1, details.preparation.single().steps.single().number)
        assertEquals("data:image/png;base64,AQID", image)
    }

    @Test
    fun absentAllergenSectionsRemainUnknown() = runTest {
        val client = PicnicClient(
            PicnicClientConfig(baseUrlOverride = "https://picnic.example.test/api/15"),
            RecordingTransport { jsonResponse(noAllergenFixture) },
            InMemoryPicnicAuthStore("token")
        )

        val details = client.catalog.getProductDetails("s2")

        assertEquals(PicnicAllergenDataStatus.UNKNOWN, details.allergens.status)
        assertTrue(details.allergens.contains.isEmpty())
        assertTrue(details.allergens.mayContain.isEmpty())
        assertNotNull(details.product)
    }
}

private fun binaryResponse(body: ByteArray) =
    com.groceryautomate.picnic.application.port.out.PicnicHttpResponse(200, emptyMap(), body)

private val searchFixture = """
{
  "id":"search-page-root-content","type":"BLOCK","children":[
    {"sellingUnit":{"id":"s1","name":"Milk","display_price":189,"unit_quantity":"1 l",
      "image_id":"img","max_count":20,"price_ranges":[{"price":189,"from_quantity":1}],
      "promotion":{"promotion_id":"promo-1","promotion_label":"20% off","price":189,
        "strikethrough_price":259,"show_strikethrough_price":true},
      "decorators":[{"type":"PROMO","text":"20% off"}]}}
  ]
}
""".trimIndent()

private val detailsFixture = """
{
  "layout":{"body":{"id":"product-details-page-root-main-container","children":[
    {"type":"RICH_TEXT","textType":"HEADER1","markdown":"Milk"},
    {"type":"RICH_TEXT","textAttributes":{"weight":"REGULAR"},"markdown":"Dairy"},
    {"type":"STACK","children":[{"type":"RICH_TEXT","markdown":"1 l"},{"type":"RICH_TEXT","markdown":"€1.89/l"}]},
    {"type":"PRICE","price":189}
  ]}},
  "sellingUnit":{"id":"s1","name":"Milk"},
  "gallery":{"id":"product-page-image-gallery-main-image-container","source":{"id":"img"}},
  "allergies":{"id":"product-page-allergies","children":[
    {"type":"RICH_TEXT","markdown":"**Bevat**"},{"type":"RICH_TEXT","markdown":"Milk"},
    {"type":"RICH_TEXT","markdown":"**Bevat mogelijk**"},{"type":"RICH_TEXT","markdown":"Nuts"},
    {"type":"RICH_TEXT","markdown":"**Voedingswaarde**"}
  ]},
  "nutrition":{"children":[
    {"type":"RICH_TEXT","markdown":"Per 100 ml"},
    {"type":"STACK","axis":"HORIZONTAL","children":[{"type":"RICH_TEXT","markdown":"Energie"},{"type":"RICH_TEXT","markdown":"206 kJ"}]},
    {"type":"STACK","axis":"HORIZONTAL","children":[{"type":"RICH_TEXT","markdown":"kcal"},{"type":"RICH_TEXT","markdown":"49 kcal"}]},
    {"type":"STACK","axis":"HORIZONTAL","children":[{"type":"RICH_TEXT","markdown":"Koolhydraten"},{"type":"RICH_TEXT","markdown":"4,8g"}]}
  ]},
  "sections":{"children":[
    {"type":"RICH_TEXT","markdown":"**Ingrediënten**\nMilk"},
    {"type":"RICH_TEXT","markdown":"**Bereiding**\n**Stap 1** Shake before use"},
    {"type":"RICH_TEXT","markdown":"**Extra informatie**"},
    {"type":"RICH_TEXT","markdown":"**Bewaren**\nKeep refrigerated"},
    {"type":"RICH_TEXT","markdown":"**Land van herkomst**\nNetherlands"}
  ]},
  "accordion":{"id":"accordion-list","items":[{"header":{"markdown":"Storage"},"body":{"markdown":"Keep refrigerated"}}]}
}
""".trimIndent()

private val noAllergenFixture = """
{"layout":{"body":{"id":"product-details-page-root-main-container","children":[
  {"type":"RICH_TEXT","textType":"HEADER1","markdown":"Apple"},{"type":"PRICE","price":99}
]}},"sellingUnit":{"id":"s2","name":"Apple"}}
""".trimIndent()

package com.groceryautomate.picnic

import com.groceryautomate.catalog.HistoricalPriceObservationId
import com.groceryautomate.picnic.domain.PicnicHistoricalPriceExtractor
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class PicnicHistoricalPriceExtractorTest {
    @Test
    fun extractsPaidOriginalQuantityAndProvenanceWithoutRounding() {
        val detail = Json.parseToJsonElement(
            """
            {"delivery_id":"delivery-1","orders":[{"id":"order-1",
             "creation_time":"2025-01-02T10:15:00+01:00","items":[
              {"id":"line-1","type":"ORDER_LINE","price":999,"display_price":999,
               "decorators":[{"type":"PRICE","display_price":949},{"type":"PROMO","text":"Bonus"}],
               "items":[{"id":"s1001","unit_quantity":"500 gram","decorators":[
                 {"type":"QUANTITY","quantity":2}]}]}
             ]}]}
            """.trimIndent()
        )

        val observation = PicnicHistoricalPriceExtractor.observations(listOf(detail)) {
            HistoricalPriceObservationId("hash-$it")
        }.single()

        assertEquals("picnic:nl:s1001", observation.productId.value)
        assertEquals(949, observation.paidLineTotal.minorUnits)
        assertEquals(999, observation.originalLineTotal?.minorUnits)
        assertEquals(2, observation.quantity)
        assertEquals("500 gram", observation.packageText)
        assertEquals("Bonus", observation.promotionLabel)
        assertEquals("2025-01-02T10:15:00+01:00", observation.purchasedAt)
    }

    @Test
    fun skipsAmbiguousAndMalformedRowsAndDeduplicatesSources() {
        val detail = Json.parseToJsonElement(
            """
            {"id":"delivery-1","orders":[{"id":"order-1","creation_time":"2025-01-02T10:15:00Z",
             "items":[
              {"id":"ambiguous","type":"ORDER_LINE","price":500,"items":[
               {"id":"s1","decorators":[{"type":"QUANTITY","quantity":1}]},
               {"id":"s2","decorators":[{"type":"QUANTITY","quantity":1}]}]},
              {"id":"valid","type":"ORDER_LINE","price":199,"items":[
               {"id":"s3","decorators":[{"type":"QUANTITY","quantity":1}]}]},
              {"id":"invalid-quantity","type":"ORDER_LINE","price":99,"items":[{"id":"s4"}]}
             ]}]}
            """.trimIndent()
        )
        val extracted = PicnicHistoricalPriceExtractor.observations(listOf(detail, detail)) {
            HistoricalPriceObservationId(it)
        }

        assertEquals(listOf("picnic:nl:s3"), extracted.map { it.productId.value })
    }
}

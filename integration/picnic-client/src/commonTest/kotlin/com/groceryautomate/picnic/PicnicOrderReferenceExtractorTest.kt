package com.groceryautomate.picnic

import com.groceryautomate.picnic.domain.PicnicOrderReferenceExtractor
import com.groceryautomate.picnic.domain.PicnicHistoricalProductReference
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class PicnicOrderReferenceExtractorTest {
    @Test
    fun extractsOnlyDirectTopLevelDeliveryIdentifiers() {
        val summary = Json.parseToJsonElement(
            """[
                {"id":"delivery-1","nested":{"id":"wrong"}},
                {"delivery_id":"delivery-2"},
                {"deliveryId":"delivery-2"},
                {"nested":{"delivery_id":"not-direct"}}
            ]"""
        )

        assertEquals(
            listOf("delivery-1", "delivery-2"),
            PicnicOrderReferenceExtractor.deliveryIds(summary)
        )
    }

    @Test
    fun extractsNestedPicnicProductReferencesAndDeduplicatesInEncounterOrder() {
        val details = listOf(
            Json.parseToJsonElement(
                """{
                    "items":[
                        {"id":"s1004201","name":"Synthetic product"},
                        {"product_id":"s1004202"},
                        {"id":"delivery-1"},
                        {"description":"s1004999"},
                        {"image_id":"s1004888"}
                    ]
                }"""
            ),
            Json.parseToJsonElement(
                """{"articleId":"s1004202","selling_unit_id":"s1004203","id":1004204}"""
            )
        )

        assertEquals(
            listOf("s1004201", "s1004202", "s1004203"),
            PicnicOrderReferenceExtractor.productIds(details)
        )
    }

    @Test
    fun unknownShapesFailClosedToEmptyReferences() {
        val unknown = Json.parseToJsonElement("""{"text":"s1004201","items":[1,2,3]}""")

        assertEquals(emptyList(), PicnicOrderReferenceExtractor.deliveryIds(unknown))
        assertEquals(emptyList(), PicnicOrderReferenceExtractor.productIds(listOf(unknown)))
    }

    @Test
    fun extractsLatestSanitizedHistoricalProductFields() {
        val details = listOf(
            Json.parseToJsonElement(
                """{"items":[{"id":"s1001","name":"Old name","unit_quantity":"300 gram", "image_ids":["image-1"]}]}"""
            ),
            Json.parseToJsonElement(
                """{"items":[{"id":"s1001","name":"Current name","unit_quantity":"0.3 kg", "image_ids":["image-2"]}]}"""
            )
        )

        assertEquals(
            listOf(PicnicHistoricalProductReference("s1001", "Current name", "0.3 kg", "image-2")),
            PicnicOrderReferenceExtractor.historicalProducts(details)
        )
    }
}

package com.groceryautomate.importer

import com.groceryautomate.catalog.HistoricalPriceObservation
import com.groceryautomate.catalog.HistoricalPriceObservationId
import com.groceryautomate.events.CommandId
import java.security.MessageDigest
import java.nio.charset.StandardCharsets
import java.util.UUID

fun importCommandId(batchId: String, product: ImportProduct): CommandId {
    val name = listOf(batchId, product.retailer.name.lowercase(), product.productId).joinToString(":")
    return CommandId(UUID.nameUUIDFromBytes(name.toByteArray(StandardCharsets.UTF_8)).toString())
}

fun historicalPriceCommandId(observation: HistoricalPriceObservation): CommandId {
    val name = "historical-price:${observation.id.value}"
    return CommandId(UUID.nameUUIDFromBytes(name.toByteArray(StandardCharsets.UTF_8)).toString())
}

fun historicalObservationId(sourceKey: String): HistoricalPriceObservationId {
    val digest = MessageDigest.getInstance("SHA-256").digest(sourceKey.toByteArray(StandardCharsets.UTF_8))
    return HistoricalPriceObservationId(digest.joinToString("") { "%02x".format(it) })
}

package com.groceryautomate.importer

import com.groceryautomate.events.CommandId
import java.nio.charset.StandardCharsets
import java.util.UUID

fun importCommandId(batchId: String, product: ImportProduct): CommandId {
    val name = listOf(batchId, product.retailer.name.lowercase(), product.productId).joinToString(":")
    return CommandId(UUID.nameUUIDFromBytes(name.toByteArray(StandardCharsets.UTF_8)).toString())
}

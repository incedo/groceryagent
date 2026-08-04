package com.groceryautomate.backend

import com.groceryautomate.catalog.ProductCatalogPort
import com.groceryautomate.catalog.ProductId
import com.groceryautomate.events.AppendCatalogEvents
import com.groceryautomate.events.AppendResult
import com.groceryautomate.events.CatalogEventRepository
import com.groceryautomate.events.CommandId
import com.groceryautomate.events.EventId
import com.groceryautomate.events.OfferObserved
import com.groceryautomate.events.ProducerId
import com.groceryautomate.events.ProductImported
import com.groceryautomate.events.ProposedCatalogEvent
import com.groceryautomate.events.StreamId
import com.groceryautomate.events.StreamVersionConflict

class CatalogImportService(
    private val provider: ProductCatalogPort,
    private val repository: CatalogEventRepository,
    private val nextEventId: () -> String,
    private val now: () -> String
) {
    suspend fun importProduct(
        providerProductId: ProductId,
        commandId: CommandId,
        producerId: ProducerId
    ): AppendResult? {
        repository.findCommand(commandId)?.let { return it }
        val product = provider.getProduct(providerProductId) ?: return null
        val streamId = StreamId("product:${product.product.id.value}")
        val occurredAt = now()
        val events = buildList {
            add(
                ProposedCatalogEvent(
                    EventId(nextEventId()),
                    occurredAt,
                    ProductImported(product.product, product.composition, product.evidence)
                )
            )
            product.offers.forEach { offer ->
                add(ProposedCatalogEvent(EventId(nextEventId()), occurredAt, OfferObserved(offer)))
            }
        }
        repeat(MAX_APPEND_ATTEMPTS) { attempt ->
            val request = AppendCatalogEvents(
                commandId = commandId,
                streamId = streamId,
                expectedVersion = repository.streamVersion(streamId),
                producerId = producerId,
                correlationId = commandId,
                events = events
            )
            try {
                return repository.append(request)
            } catch (conflict: StreamVersionConflict) {
                if (attempt == MAX_APPEND_ATTEMPTS - 1) throw conflict
            }
        }
        error("Catalog import retry loop terminated unexpectedly.")
    }

    private companion object {
        const val MAX_APPEND_ATTEMPTS = 3
    }
}

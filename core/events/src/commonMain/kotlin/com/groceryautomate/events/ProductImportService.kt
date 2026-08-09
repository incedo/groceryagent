package com.groceryautomate.events

import com.groceryautomate.catalog.ProductCatalogPort
import com.groceryautomate.catalog.ProductId

class ProductImportService(
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
        error("Product import retry loop terminated unexpectedly.")
    }

    private companion object {
        const val MAX_APPEND_ATTEMPTS = 3
    }
}

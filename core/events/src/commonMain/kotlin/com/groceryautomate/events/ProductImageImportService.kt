package com.groceryautomate.events

import com.groceryautomate.catalog.ProductImageAsset

class ProductImageImportService(
    private val repository: CatalogEventRepository,
    private val nextEventId: () -> String
) {
    suspend fun record(
        asset: ProductImageAsset,
        commandId: CommandId,
        producerId: ProducerId
    ): AppendResult {
        repository.findCommand(commandId)?.let { return it }
        val streamId = StreamId("product:${asset.productId.value}")
        repeat(MAX_APPEND_ATTEMPTS) { attempt ->
            try {
                return repository.append(
                    AppendCatalogEvents(
                        commandId = commandId,
                        streamId = streamId,
                        expectedVersion = repository.streamVersion(streamId),
                        producerId = producerId,
                        correlationId = commandId,
                        events = listOf(
                            ProposedCatalogEvent(
                                EventId(nextEventId()),
                                asset.observedAt,
                                ProductImageStored(asset)
                            )
                        )
                    )
                )
            } catch (conflict: StreamVersionConflict) {
                if (attempt == MAX_APPEND_ATTEMPTS - 1) throw conflict
            }
        }
        error("Product image append retry loop terminated unexpectedly.")
    }

    private companion object {
        const val MAX_APPEND_ATTEMPTS = 3
    }
}

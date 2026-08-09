package com.groceryautomate.events

import com.groceryautomate.catalog.HistoricalPriceObservation

class HistoricalPriceImportService(
    private val repository: CatalogEventRepository,
    private val nextEventId: () -> String
) {
    suspend fun record(
        observation: HistoricalPriceObservation,
        commandId: CommandId,
        producerId: ProducerId
    ): AppendResult {
        repository.findCommand(commandId)?.let { return it }
        val streamId = StreamId("price-history:${observation.id.value}")
        val request = AppendCatalogEvents(
            commandId = commandId,
            streamId = streamId,
            expectedVersion = repository.streamVersion(streamId),
            producerId = producerId,
            correlationId = commandId,
            events = listOf(
                ProposedCatalogEvent(
                    EventId(nextEventId()),
                    observation.purchasedAt,
                    HistoricalPriceObserved(observation)
                )
            )
        )
        return repository.append(request)
    }
}

package com.groceryautomate.events

import com.groceryautomate.catalog.ProductCatalogPort
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ProposedCatalogEvent(
    val eventId: EventId,
    val occurredAt: String,
    val event: CatalogEvent
)

@Serializable
data class AppendCatalogEvents(
    val commandId: CommandId,
    val streamId: StreamId,
    val expectedVersion: Long,
    val producerId: ProducerId,
    val correlationId: CommandId,
    val causationId: EventId? = null,
    val events: List<ProposedCatalogEvent>
) {
    init {
        require(expectedVersion >= 0) { "Expected stream version must not be negative." }
        require(events.isNotEmpty()) { "At least one event is required." }
    }
}

@Serializable
data class EventEnvelope(
    val globalPosition: Long,
    val streamId: StreamId,
    val streamVersion: Long,
    val eventId: EventId,
    val eventType: String,
    val schemaVersion: Int,
    val producerId: ProducerId,
    val occurredAt: String,
    val correlationId: CommandId,
    val causationId: EventId?,
    val payload: JsonObject
)

@Serializable
data class AppendResult(
    val streamId: StreamId,
    val streamVersion: Long,
    val firstGlobalPosition: Long,
    val lastGlobalPosition: Long,
    val eventCount: Int,
    val duplicateCommand: Boolean
)

@Serializable
data class EventPage(
    val after: Long,
    val nextCursor: Long,
    val events: List<EventEnvelope>
)

interface CatalogEventRepository : ProductCatalogPort {
    suspend fun findCommand(commandId: CommandId): AppendResult?
    suspend fun streamVersion(streamId: StreamId): Long
    suspend fun append(request: AppendCatalogEvents): AppendResult
    suspend fun readEvents(after: Long, limit: Int): EventPage
    suspend fun rebuildProjections(): Int
}

class StreamVersionConflict(
    val streamId: StreamId,
    val expected: Long,
    val actual: Long
) : IllegalStateException("Stream ${streamId.value} expected version $expected but was $actual.")

class CommandConflict(message: String) : IllegalStateException(message)

package com.groceryautomate.postgres

import com.groceryautomate.events.CatalogEventCodec
import com.groceryautomate.events.CommandId
import com.groceryautomate.events.EventEnvelope
import com.groceryautomate.events.EventId
import com.groceryautomate.events.ProducerId
import com.groceryautomate.events.StreamId
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.sql.ResultSet

internal fun ResultSet.toEventEnvelope(): EventEnvelope = EventEnvelope(
    globalPosition = getLong("global_position"),
    streamId = StreamId(getString("stream_id")),
    streamVersion = getLong("stream_version"),
    eventId = EventId(getString("event_id")),
    eventType = getString("event_type"),
    schemaVersion = getInt("schema_version"),
    producerId = ProducerId(getString("producer_id")),
    occurredAt = getObject("occurred_at", java.time.OffsetDateTime::class.java).toInstant().toString(),
    correlationId = CommandId(getString("correlation_id")),
    causationId = getString("causation_id")?.let(::EventId),
    payload = CatalogEventCodec.json.parseToJsonElement(getString("payload")).jsonObject
)

internal val eventSelectColumns = """
    global_position, stream_id, stream_version, event_id, event_type, schema_version,
    producer_id, occurred_at, correlation_id, causation_id, payload
""".trimIndent()

package com.groceryautomate.postgres

import com.groceryautomate.events.AppendCatalogEvents
import com.groceryautomate.events.AppendResult
import com.groceryautomate.events.CatalogEventCodec
import com.groceryautomate.events.CommandConflict
import com.groceryautomate.events.EventEnvelope
import com.groceryautomate.events.StreamVersionConflict
import java.sql.Connection
import java.time.OffsetDateTime
import java.util.UUID

internal class PostgresEventAppender(
    private val connection: Connection
) {
    fun append(request: AppendCatalogEvents): AppendResult {
        reserveCommand(request)?.let { return it }
        ensureStream(request.streamId.value)
        val actualVersion = lockStream(request.streamId.value)
        if (actualVersion != request.expectedVersion) {
            throw StreamVersionConflict(request.streamId, request.expectedVersion, actualVersion)
        }
        val writer = PostgresProjectionWriter(connection)
        val positions = request.events.mapIndexed { index, proposed ->
            val streamVersion = actualVersion + index + 1
            insertEvent(request, proposed, streamVersion).also(writer::apply).globalPosition
        }
        val finalVersion = actualVersion + request.events.size
        connection.prepareStatement(
            "UPDATE event_streams SET current_version = ? WHERE stream_id = ?"
        ).use {
            it.setLong(1, finalVersion)
            it.setString(2, request.streamId.value)
            it.executeUpdate()
        }
        connection.prepareStatement(
            """
            UPDATE event_commands SET stream_version = ?, first_global_position = ?,
                last_global_position = ?, event_count = ? WHERE command_id = ?
            """.trimIndent()
        ).use {
            it.setLong(1, finalVersion)
            it.setLong(2, positions.first())
            it.setLong(3, positions.last())
            it.setInt(4, positions.size)
            it.setObject(5, UUID.fromString(request.commandId.value))
            it.executeUpdate()
        }
        return AppendResult(
            request.streamId,
            finalVersion,
            positions.first(),
            positions.last(),
            positions.size,
            duplicateCommand = false
        )
    }

    private fun reserveCommand(request: AppendCatalogEvents): AppendResult? {
        val inserted = connection.prepareStatement(
            "INSERT INTO event_commands(command_id, stream_id) VALUES (?, ?) ON CONFLICT DO NOTHING"
        ).use {
            it.setObject(1, UUID.fromString(request.commandId.value))
            it.setString(2, request.streamId.value)
            it.executeUpdate()
        }
        if (inserted == 1) return null
        return connection.prepareStatement(
            """
            SELECT stream_id, stream_version, first_global_position, last_global_position, event_count
            FROM event_commands WHERE command_id = ?
            """.trimIndent()
        ).use {
            it.setObject(1, UUID.fromString(request.commandId.value))
            it.executeQuery().use { result ->
                check(result.next()) { "Conflicting command disappeared." }
                val existingStream = result.getString("stream_id")
                if (existingStream != request.streamId.value) {
                    throw CommandConflict("Command id was already used for another stream.")
                }
                AppendResult(
                    request.streamId,
                    result.getLong("stream_version"),
                    result.getLong("first_global_position"),
                    result.getLong("last_global_position"),
                    result.getInt("event_count"),
                    duplicateCommand = true
                )
            }
        }
    }

    private fun ensureStream(streamId: String) {
        connection.prepareStatement(
            "INSERT INTO event_streams(stream_id, current_version) VALUES (?, 0) ON CONFLICT DO NOTHING"
        ).use {
            it.setString(1, streamId)
            it.executeUpdate()
        }
    }

    private fun lockStream(streamId: String): Long = connection.prepareStatement(
        "SELECT current_version FROM event_streams WHERE stream_id = ? FOR UPDATE"
    ).use {
        it.setString(1, streamId)
        it.executeQuery().use { result ->
            check(result.next()) { "Stream was not created." }
            result.getLong(1)
        }
    }

    private fun insertEvent(
        request: AppendCatalogEvents,
        proposed: com.groceryautomate.events.ProposedCatalogEvent,
        streamVersion: Long
    ): EventEnvelope {
        val event = proposed.event
        val payload = CatalogEventCodec.encode(event)
        return connection.prepareStatement(
            """
            INSERT INTO domain_events(
                event_id, stream_id, stream_version, event_type, schema_version, producer_id,
                occurred_at, correlation_id, causation_id, payload
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
            RETURNING $eventSelectColumns
            """.trimIndent()
        ).use {
            it.setObject(1, UUID.fromString(proposed.eventId.value))
            it.setString(2, request.streamId.value)
            it.setLong(3, streamVersion)
            it.setString(4, event.eventType)
            it.setInt(5, event.schemaVersion)
            it.setString(6, request.producerId.value)
            it.setObject(7, OffsetDateTime.parse(proposed.occurredAt))
            it.setObject(8, UUID.fromString(request.correlationId.value))
            it.setObject(9, request.causationId?.value?.let(UUID::fromString))
            it.setString(10, payload.toString())
            it.executeQuery().use { result ->
                check(result.next()) { "Event append returned no row." }
                result.toEventEnvelope()
            }
        }
    }
}

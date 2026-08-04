package com.groceryautomate.events

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

private val uuidPattern = Regex(
    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
)

@Serializable
@JvmInline
value class EventId(val value: String) {
    init {
        require(uuidPattern.matches(value)) { "Event id must be a UUID." }
    }
}

@Serializable
@JvmInline
value class CommandId(val value: String) {
    init {
        require(uuidPattern.matches(value)) { "Command id must be a UUID." }
    }
}

@Serializable
@JvmInline
value class StreamId(val value: String) {
    init {
        require(value.isNotBlank()) { "Stream id must not be blank." }
    }
}

@Serializable
@JvmInline
value class ProducerId(val value: String) {
    init {
        require(value.isNotBlank()) { "Producer id must not be blank." }
    }
}

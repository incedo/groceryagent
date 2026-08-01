package com.groceryautomate.picnic.application.service

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

internal fun JsonElement.objectsNamed(name: String): Sequence<JsonObject> = sequence {
    when (this@objectsNamed) {
        is JsonObject -> for ((key, value) in this@objectsNamed) {
            if (key == name && value is JsonObject) yield(value)
            yieldAll(value.objectsNamed(name))
        }
        is JsonArray -> this@objectsNamed.forEach { yieldAll(it.objectsNamed(name)) }
        else -> Unit
    }
}

internal fun JsonElement.valuesNamed(name: String): Sequence<JsonElement> = sequence {
    when (this@valuesNamed) {
        is JsonObject -> for ((key, value) in this@valuesNamed) {
            if (key == name) yield(value)
            yieldAll(value.valuesNamed(name))
        }
        is JsonArray -> this@valuesNamed.forEach { yieldAll(it.valuesNamed(name)) }
        else -> Unit
    }
}

internal fun JsonElement.findObjectById(id: String): JsonObject? {
    when (this) {
        is JsonObject -> {
            if (string("id") == id) return this
            values.forEach { value -> value.findObjectById(id)?.let { return it } }
        }
        is JsonArray -> forEach { value -> value.findObjectById(id)?.let { return it } }
        else -> Unit
    }
    return null
}

internal fun JsonElement.allObjects(): Sequence<JsonObject> = sequence {
    when (this@allObjects) {
        is JsonObject -> {
            yield(this@allObjects)
            values.forEach { yieldAll(it.allObjects()) }
        }
        is JsonArray -> forEach { yieldAll(it.allObjects()) }
        else -> Unit
    }
}

internal fun JsonElement.markdowns(): List<String> = valuesNamed("markdown")
    .mapNotNull { (it as? JsonPrimitive)?.content }
    .toList()

internal fun JsonObject.string(name: String): String = (this[name] as? JsonPrimitive)?.content.orEmpty()
internal fun JsonObject.int(name: String): Int = (this[name] as? JsonPrimitive)?.intOrNull ?: 0
internal fun JsonObject.intOrNull(name: String): Int? =
    (this[name] as? JsonPrimitive)?.compatibleIntOrNull()
internal fun JsonObject.booleanOrNull(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.booleanOrNull
internal fun JsonObject.stringOrNull(name: String): String? = string(name).takeIf(String::isNotBlank)

internal fun stripColorMarkup(value: String): String = value
    .replace(Regex("#\\([A-Za-z0-9#_]+\\)"), "")
    .trim()

internal fun stripMarkdown(value: String): String = value.replace("**", "").replace("__", "")

internal fun JsonPrimitive.compatibleIntOrNull(): Int? {
    intOrNull?.let { return it }
    val parts = content.split('.', limit = 2)
    if (parts.size != 2 || parts[1].any { it != '0' }) return null
    return parts[0].toIntOrNull()
}

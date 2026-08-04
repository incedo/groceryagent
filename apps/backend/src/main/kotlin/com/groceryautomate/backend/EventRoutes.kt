package com.groceryautomate.backend

import com.groceryautomate.events.CatalogEventRepository
import com.groceryautomate.events.EventPage
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

private const val DEFAULT_EVENT_LIMIT = 100
private const val MAX_EVENT_LIMIT = 1000

internal fun Route.eventRoutes(repository: CatalogEventRepository) {
    get("/api/v1/events") {
        val after = call.request.queryParameters["after"]?.toLongOrNull() ?: 0L
        if (after < 0) throw InvalidCatalogRequest("Event cursor must not be negative.")
        val limitText = call.request.queryParameters["limit"]
        val limit = limitText?.toIntOrNull() ?: DEFAULT_EVENT_LIMIT
        if (limit !in 1..MAX_EVENT_LIMIT || (limitText != null && limitText.toIntOrNull() == null)) {
            throw InvalidCatalogRequest("Event limit must be between 1 and $MAX_EVENT_LIMIT.")
        }
        call.respondJson(EventPage.serializer(), repository.readEvents(after, limit))
    }
}

package com.groceryautomate.backend

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

internal fun Route.healthRoutes(readiness: suspend () -> Boolean) {
    get("/health/live") {
        call.respondJson(HealthStatus.serializer(), HealthStatus("up"))
    }
    get("/health/ready") {
        if (readiness()) {
            call.respondJson(HealthStatus.serializer(), HealthStatus("ready"))
        } else {
            call.respondJson(HealthStatus.serializer(), HealthStatus("not-ready"), HttpStatusCode.ServiceUnavailable)
        }
    }
}

@Serializable
data class HealthStatus(val status: String)

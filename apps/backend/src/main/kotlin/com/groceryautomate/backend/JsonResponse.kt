package com.groceryautomate.backend

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json

private val responseJson = Json {
    encodeDefaults = true
    explicitNulls = true
}

internal suspend fun <T> ApplicationCall.respondJson(
    serializer: SerializationStrategy<T>,
    value: T,
    status: HttpStatusCode = HttpStatusCode.OK
) {
    respondText(
        text = responseJson.encodeToString(serializer, value),
        contentType = ContentType.Application.Json,
        status = status
    )
}

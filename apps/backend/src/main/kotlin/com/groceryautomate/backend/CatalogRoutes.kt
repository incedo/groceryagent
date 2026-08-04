package com.groceryautomate.backend

import com.groceryautomate.catalog.ProductCatalogPort
import com.groceryautomate.catalog.ProductId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val DEFAULT_SEARCH_LIMIT = 20
private const val MAX_SEARCH_LIMIT = 100

fun Application.catalogModule(catalog: ProductCatalogPort) {
    install(ContentNegotiation) {
        json(Json {
            encodeDefaults = true
            explicitNulls = true
        })
    }
    install(StatusPages) {
        exception<CancellationException> { _, cause -> throw cause }
        exception<InvalidCatalogRequest> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ApiError("INVALID_REQUEST", cause.message ?: "Invalid catalog request.")
            )
        }
        exception<IllegalArgumentException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ApiError("INVALID_REQUEST", "Invalid catalog request."))
        }
        exception<Throwable> { call, _ ->
            call.respond(
                HttpStatusCode.BadGateway,
                ApiError("PROVIDER_UNAVAILABLE", "The product provider is temporarily unavailable.")
            )
        }
    }
    routing {
        route("/api/v1/products") {
            get {
                val query = call.request.queryParameters["query"]
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?: throw InvalidCatalogRequest("Query parameter 'query' is required.")
                val limit = call.request.queryParameters["limit"].toSearchLimit()
                call.respond(catalog.search(query, limit))
            }
            get("/{id}") {
                val id = call.parameters["id"]
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?: throw InvalidCatalogRequest("Product id is required.")
                val product = catalog.getProduct(ProductId(id))
                if (product == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiError("PRODUCT_NOT_FOUND", "Product was not found.")
                    )
                } else {
                    call.respond(product)
                }
            }
        }
    }
}

private fun String?.toSearchLimit(): Int {
    if (this == null) return DEFAULT_SEARCH_LIMIT
    return toIntOrNull()?.takeIf { it in 1..MAX_SEARCH_LIMIT }
        ?: throw InvalidCatalogRequest("Query parameter 'limit' must be between 1 and $MAX_SEARCH_LIMIT.")
}

private class InvalidCatalogRequest(message: String) : IllegalArgumentException(message)

@Serializable
data class ApiError(
    val code: String,
    val message: String
)

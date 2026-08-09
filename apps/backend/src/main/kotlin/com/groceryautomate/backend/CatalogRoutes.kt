package com.groceryautomate.backend

import com.groceryautomate.catalog.ProductCatalogPort
import com.groceryautomate.catalog.ProductId
import com.groceryautomate.catalog.CatalogProduct
import com.groceryautomate.catalog.ProductSearchResult
import com.groceryautomate.events.AppendResult
import com.groceryautomate.events.CatalogEventRepository
import com.groceryautomate.events.CommandId
import com.groceryautomate.events.ProducerId
import com.groceryautomate.events.ProductImportService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

private const val DEFAULT_SEARCH_LIMIT = 20
private const val MAX_SEARCH_LIMIT = 100

internal fun Route.catalogRoutes(
    repository: CatalogEventRepository,
    provider: ProductCatalogPort,
    imports: ProductImportService
) {
    route("/api/v1/catalog/products") {
        get {
            val query = call.requiredQuery()
            call.respondJson(ProductSearchResult.serializer(), repository.search(query, call.searchLimit()))
        }
        get("/{id}") {
            val product = repository.getProduct(ProductId(call.requiredProductId()))
            if (product == null) call.productNotFound() else call.respondJson(CatalogProduct.serializer(), product)
        }
    }
    route("/api/v1/retailers/picnic/products") {
        get {
            call.respondJson(ProductSearchResult.serializer(), provider.search(call.requiredQuery(), call.searchLimit()))
        }
        post("/{id}/imports") {
            val commandId = call.request.header("Idempotency-Key")
                ?.trim()?.takeIf(String::isNotEmpty)?.let(::CommandId)
                ?: throw InvalidCatalogRequest("Idempotency-Key header is required.")
            val producerId = call.request.header("X-Producer-Id")
                ?.trim()?.takeIf(String::isNotEmpty)?.let(::ProducerId)
                ?: ProducerId("backend-api")
            val result = imports.importProduct(ProductId(call.requiredProductId()), commandId, producerId)
            if (result == null) call.productNotFound() else call.respondJson(AppendResult.serializer(), result, HttpStatusCode.Accepted)
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.requiredQuery(): String =
    request.queryParameters["query"]?.trim()?.takeIf(String::isNotEmpty)
        ?: throw InvalidCatalogRequest("Query parameter 'query' is required.")

private fun io.ktor.server.application.ApplicationCall.searchLimit(): Int {
    val value = request.queryParameters["limit"] ?: return DEFAULT_SEARCH_LIMIT
    return value.toIntOrNull()?.takeIf { it in 1..MAX_SEARCH_LIMIT }
        ?: throw InvalidCatalogRequest("Query parameter 'limit' must be between 1 and $MAX_SEARCH_LIMIT.")
}

private fun io.ktor.server.application.ApplicationCall.requiredProductId(): String =
    parameters["id"]?.trim()?.takeIf(String::isNotEmpty)
        ?: throw InvalidCatalogRequest("Product id is required.")

private suspend fun io.ktor.server.application.ApplicationCall.productNotFound() {
    respondJson(ApiError.serializer(), ApiError("PRODUCT_NOT_FOUND", "Product was not found."), HttpStatusCode.NotFound)
}

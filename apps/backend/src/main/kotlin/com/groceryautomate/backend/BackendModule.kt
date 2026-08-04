package com.groceryautomate.backend

import com.groceryautomate.catalog.ProductCatalogPort
import com.groceryautomate.events.CatalogEventRepository
import com.groceryautomate.events.CommandConflict
import com.groceryautomate.events.StreamVersionConflict
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.routing.routing
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import java.sql.SQLException

fun Application.catalogModule(
    repository: CatalogEventRepository,
    provider: ProductCatalogPort,
    imports: CatalogImportService,
    readiness: suspend () -> Boolean
) {
    install(StatusPages) {
        exception<CancellationException> { _, cause -> throw cause }
        exception<InvalidCatalogRequest> { call, cause ->
            call.respondJson(ApiError.serializer(), ApiError("INVALID_REQUEST", cause.message.orEmpty()), HttpStatusCode.BadRequest)
        }
        exception<IllegalArgumentException> { call, _ ->
            call.respondJson(ApiError.serializer(), ApiError("INVALID_REQUEST", "Invalid request."), HttpStatusCode.BadRequest)
        }
        exception<CommandConflict> { call, _ ->
            call.respondJson(ApiError.serializer(), ApiError("COMMAND_CONFLICT", "Command conflicts with prior use."), HttpStatusCode.Conflict)
        }
        exception<StreamVersionConflict> { call, _ ->
            call.respondJson(ApiError.serializer(), ApiError("STREAM_CONFLICT", "Catalog state changed concurrently."), HttpStatusCode.Conflict)
        }
        exception<CatalogProviderUnavailable> { call, _ ->
            call.respondJson(ApiError.serializer(), ApiError("PROVIDER_UNAVAILABLE", "The product provider is temporarily unavailable."), HttpStatusCode.ServiceUnavailable)
        }
        exception<SQLException> { call, _ ->
            call.respondJson(ApiError.serializer(), ApiError("DATABASE_UNAVAILABLE", "The catalog database is temporarily unavailable."), HttpStatusCode.ServiceUnavailable)
        }
        exception<Throwable> { call, _ ->
            call.respondJson(ApiError.serializer(), ApiError("INTERNAL_ERROR", "The service could not complete the request."), HttpStatusCode.InternalServerError)
        }
    }
    routing {
        catalogRoutes(repository, provider, imports)
        eventRoutes(repository)
        healthRoutes(readiness)
    }
}

internal class InvalidCatalogRequest(message: String) : IllegalArgumentException(message)

@Serializable
data class ApiError(val code: String, val message: String)

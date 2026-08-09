package com.groceryautomate.importer

enum class ImportFailureCategory {
    PROVIDER_NOT_FOUND,
    PROVIDER_ROUTES_EXHAUSTED,
    PROVIDER_ROUTE_UNAVAILABLE,
    PROVIDER_UNAUTHORIZED,
    PROVIDER_VALIDATION,
    PROVIDER_CONFLICT,
    PROVIDER_RATE_LIMITED,
    PROVIDER_SERVER,
    PROVIDER_MAPPING_INCOMPATIBLE,
    PROVIDER_OTHER,
    UNEXPECTED
}

data class ImportRouteFailure(
    val route: String,
    val statusCode: Int?,
    val reason: String
)

data class ImportFailureDiagnostic(
    val category: ImportFailureCategory,
    val statusCode: Int? = null,
    val exceptionType: String? = null,
    val routeAttempts: List<ImportRouteFailure> = emptyList()
)

internal fun providerNotFoundDiagnostic() = ImportFailureDiagnostic(
    ImportFailureCategory.PROVIDER_NOT_FOUND
)

internal fun unexpectedFailureDiagnostic(failure: Throwable) = ImportFailureDiagnostic(
    category = ImportFailureCategory.UNEXPECTED,
    exceptionType = failure::class.simpleName ?: "UnknownException"
)

package com.groceryautomate.importer

import com.groceryautomate.picnic.domain.PicnicApiException
import com.groceryautomate.picnic.domain.PicnicCompatibilityException
import com.groceryautomate.picnic.domain.PicnicMappingException

internal fun classifyPicnicImportFailure(failure: Throwable): ImportFailureDiagnostic = when (failure) {
    is PicnicCompatibilityException -> ImportFailureDiagnostic(
        category = ImportFailureCategory.PROVIDER_ROUTES_EXHAUSTED,
        routeAttempts = failure.attempts.map { attempt ->
            ImportRouteFailure(
                route = attempt.generation.name,
                statusCode = attempt.statusCode,
                reason = attempt.reason.name
            )
        }
    )
    is PicnicApiException -> ImportFailureDiagnostic(
        category = failure.statusCode.toFailureCategory(),
        statusCode = failure.statusCode
    )
    is PicnicMappingException -> ImportFailureDiagnostic(
        category = ImportFailureCategory.PROVIDER_MAPPING_INCOMPATIBLE
    )
    else -> unexpectedFailureDiagnostic(failure)
}

private fun Int.toFailureCategory(): ImportFailureCategory = when (this) {
    404, 405, 410 -> ImportFailureCategory.PROVIDER_ROUTE_UNAVAILABLE
    401, 403 -> ImportFailureCategory.PROVIDER_UNAUTHORIZED
    400, 422 -> ImportFailureCategory.PROVIDER_VALIDATION
    409 -> ImportFailureCategory.PROVIDER_CONFLICT
    429 -> ImportFailureCategory.PROVIDER_RATE_LIMITED
    in 500..599 -> ImportFailureCategory.PROVIDER_SERVER
    else -> ImportFailureCategory.PROVIDER_OTHER
}

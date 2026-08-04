package com.groceryautomate.picnic.domain

enum class PicnicRouteGeneration {
    CURRENT,
    LEGACY
}

enum class PicnicFailureReason {
    ROUTE_UNAVAILABLE,
    UNAUTHORIZED,
    VALIDATION,
    CONFLICT,
    RATE_LIMITED,
    SERVER,
    MAPPING_INCOMPATIBLE,
    OTHER
}

data class PicnicRouteAttempt(
    val generation: PicnicRouteGeneration,
    val statusCode: Int?,
    val reason: PicnicFailureReason
)

class PicnicMappingException(message: String) : RuntimeException(message)

class PicnicCompatibilityException(
    val attempts: List<PicnicRouteAttempt>,
    message: String = "Current and legacy Picnic routes could not satisfy the operation."
) : RuntimeException(message)

internal fun Throwable.fallbackReason(): PicnicFailureReason = when (this) {
    is PicnicMappingException -> PicnicFailureReason.MAPPING_INCOMPATIBLE
    is PicnicApiException -> when (statusCode) {
        404, 405, 410 -> PicnicFailureReason.ROUTE_UNAVAILABLE
        401, 403 -> PicnicFailureReason.UNAUTHORIZED
        400, 422 -> PicnicFailureReason.VALIDATION
        409 -> PicnicFailureReason.CONFLICT
        429 -> PicnicFailureReason.RATE_LIMITED
        in 500..599 -> PicnicFailureReason.SERVER
        else -> PicnicFailureReason.OTHER
    }
    else -> PicnicFailureReason.OTHER
}

internal fun Throwable.isReadFallbackEligible(): Boolean = when (fallbackReason()) {
    PicnicFailureReason.ROUTE_UNAVAILABLE,
    PicnicFailureReason.MAPPING_INCOMPATIBLE -> true
    else -> false
}

internal fun Throwable.routeAttempt(generation: PicnicRouteGeneration) = PicnicRouteAttempt(
    generation = generation,
    statusCode = (this as? PicnicApiException)?.statusCode,
    reason = fallbackReason()
)

package com.groceryautomate.importer

internal fun ImportItemResult.toLogLine(): String = buildString {
    append("product=").append(productId)
    append(" status=").append(status)
    append(" events=").append(eventCount)
    failure?.let(::appendDiagnostic)
}

internal fun StringBuilder.appendDiagnostic(diagnostic: ImportFailureDiagnostic) {
    append(" failure_category=").append(diagnostic.category)
    diagnostic.statusCode?.let { append(" http_status=").append(it) }
    diagnostic.exceptionType?.let { append(" exception_type=").append(it) }
    if (diagnostic.routeAttempts.isNotEmpty()) {
        append(" route_attempts=")
        append(diagnostic.routeAttempts.joinToString(",") { it.toLogValue() })
    }
}

private fun ImportRouteFailure.toLogValue(): String = buildString {
    append(route)
    append('/')
    append(statusCode ?: "none")
    append('/')
    append(reason)
}

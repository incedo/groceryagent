package com.groceryautomate.picnic.application.service

import com.groceryautomate.picnic.domain.PicnicCompatibilityException
import com.groceryautomate.picnic.domain.PicnicRouteGeneration
import com.groceryautomate.picnic.domain.isReadFallbackEligible
import com.groceryautomate.picnic.domain.routeAttempt
import kotlinx.coroutines.CancellationException

internal suspend fun <T> currentFirstRead(
    current: suspend () -> T,
    legacy: suspend () -> T
): T = try {
    current()
} catch (currentFailure: Throwable) {
    if (!currentFailure.isReadFallbackEligible()) throw currentFailure
    try {
        legacy()
    } catch (legacyFailure: Throwable) {
        if (legacyFailure is CancellationException) throw legacyFailure
        throw PicnicCompatibilityException(
            attempts = listOf(
                currentFailure.routeAttempt(PicnicRouteGeneration.CURRENT),
                legacyFailure.routeAttempt(PicnicRouteGeneration.LEGACY)
            )
        )
    }
}

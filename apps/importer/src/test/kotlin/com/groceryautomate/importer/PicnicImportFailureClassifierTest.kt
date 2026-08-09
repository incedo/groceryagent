package com.groceryautomate.importer

import com.groceryautomate.picnic.domain.PicnicApiException
import com.groceryautomate.picnic.domain.PicnicCompatibilityException
import com.groceryautomate.picnic.domain.PicnicFailureReason
import com.groceryautomate.picnic.domain.PicnicMappingException
import com.groceryautomate.picnic.domain.PicnicRouteAttempt
import com.groceryautomate.picnic.domain.PicnicRouteGeneration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PicnicImportFailureClassifierTest {
    @Test
    fun retainsSafeCurrentAndLegacyRouteDiagnostics() {
        val failure = PicnicCompatibilityException(
            listOf(
                PicnicRouteAttempt(
                    PicnicRouteGeneration.CURRENT,
                    404,
                    PicnicFailureReason.ROUTE_UNAVAILABLE
                ),
                PicnicRouteAttempt(
                    PicnicRouteGeneration.LEGACY,
                    410,
                    PicnicFailureReason.ROUTE_UNAVAILABLE
                )
            )
        )

        val diagnostic = classifyPicnicImportFailure(failure)

        assertEquals(ImportFailureCategory.PROVIDER_ROUTES_EXHAUSTED, diagnostic.category)
        assertEquals(listOf("CURRENT", "LEGACY"), diagnostic.routeAttempts.map { it.route })
        assertEquals(listOf(404, 410), diagnostic.routeAttempts.map { it.statusCode })
        assertEquals(
            listOf("ROUTE_UNAVAILABLE", "ROUTE_UNAVAILABLE"),
            diagnostic.routeAttempts.map { it.reason }
        )
        assertEquals(
            "product=s1 status=FAILED events=0 failure_category=PROVIDER_ROUTES_EXHAUSTED " +
                "route_attempts=CURRENT/404/ROUTE_UNAVAILABLE,LEGACY/410/ROUTE_UNAVAILABLE",
            ImportItemResult("s1", ImportStatus.FAILED, failure = diagnostic).toLogLine()
        )
    }

    @Test
    fun classifiesOtherDirectProviderStatuses() {
        val expected = mapOf(
            404 to ImportFailureCategory.PROVIDER_ROUTE_UNAVAILABLE,
            401 to ImportFailureCategory.PROVIDER_UNAUTHORIZED,
            422 to ImportFailureCategory.PROVIDER_VALIDATION,
            409 to ImportFailureCategory.PROVIDER_CONFLICT,
            503 to ImportFailureCategory.PROVIDER_SERVER,
            418 to ImportFailureCategory.PROVIDER_OTHER
        )

        expected.forEach { (status, category) ->
            val diagnostic = classifyPicnicImportFailure(
                PicnicApiException(status, "private", "private", "private")
            )
            assertEquals(category, diagnostic.category)
            assertEquals(status, diagnostic.statusCode)
        }
    }

    @Test
    fun classifiesDirectRateLimitingWithoutProviderText() {
        val secretText = "sensitive provider response"
        val diagnostic = classifyPicnicImportFailure(
            PicnicApiException(429, secretText, secretText, "TOO_MANY_REQUESTS")
        )
        val line = ImportItemResult("s1", ImportStatus.FAILED, failure = diagnostic).toLogLine()

        assertEquals(ImportFailureCategory.PROVIDER_RATE_LIMITED, diagnostic.category)
        assertEquals(429, diagnostic.statusCode)
        assertFalse(secretText in line)
        assertEquals(
            "product=s1 status=FAILED events=0 failure_category=PROVIDER_RATE_LIMITED http_status=429",
            line
        )
    }

    @Test
    fun distinguishesMappingAndUnexpectedFailuresWithoutMessages() {
        val mapping = classifyPicnicImportFailure(PicnicMappingException("private payload"))
        val unexpected = classifyPicnicImportFailure(IllegalStateException("private database text"))
        val line = ImportItemResult("s2", ImportStatus.FAILED, failure = unexpected).toLogLine()

        assertEquals(ImportFailureCategory.PROVIDER_MAPPING_INCOMPATIBLE, mapping.category)
        assertEquals(ImportFailureCategory.UNEXPECTED, unexpected.category)
        assertEquals("IllegalStateException", unexpected.exceptionType)
        assertFalse("private" in line)
    }
}

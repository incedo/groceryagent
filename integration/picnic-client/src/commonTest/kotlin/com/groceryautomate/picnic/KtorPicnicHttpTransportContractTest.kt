package com.groceryautomate.picnic

import com.groceryautomate.picnic.adapter.out.http.KtorPicnicHttpTransport
import com.groceryautomate.picnic.application.port.out.PicnicHttpRequest
import com.groceryautomate.picnic.domain.PicnicClientConfig
import com.groceryautomate.picnic.domain.PicnicRouteGeneration
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KtorPicnicHttpTransportContractTest {
    @Test
    fun forwardsRequestAndPreservesBinaryResponse() = runTest {
        val requestBody = byteArrayOf(0, 1, 2, 127, -1)
        val responseBody = byteArrayOf(-1, 0, 42, 100)
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Patch, request.method)
            assertEquals("https://picnic.example.test/api/15/cart?mode=replace", request.url.toString())
            assertEquals("fixture-token", request.headers["x-picnic-auth"])
            assertEquals("contract", request.headers["X-Client-Marker"])
            val body = request.body as OutgoingContent.ByteArrayContent
            assertEquals(ContentType.Application.OctetStream, body.contentType)
            assertContentEquals(requestBody, body.bytes())
            respond(
                content = ByteReadChannel(responseBody),
                status = HttpStatusCode.Accepted,
                headers = headersOf("X-Picnic-Trace", listOf("first", "second"))
            )
        }
        val client = HttpClient(engine)

        try {
            val response = KtorPicnicHttpTransport(client).execute(
                PicnicHttpRequest(
                    method = "PATCH",
                    url = "https://picnic.example.test/api/15/cart?mode=replace",
                    headers = mapOf(
                        "x-picnic-auth" to "fixture-token",
                        "X-Client-Marker" to "contract",
                        "Content-Type" to "application/octet-stream"
                    ),
                    body = requestBody
                )
            )

            assertEquals(202, response.statusCode)
            assertEquals(listOf("first", "second"), response.headers["X-Picnic-Trace"])
            assertContentEquals(responseBody, response.body)
        } finally {
            client.close()
        }
    }

    @Test
    fun omitsRequestBodyWhenPortBodyIsAbsent() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertNull((request.body as? OutgoingContent.ByteArrayContent)?.bytes())
            respond("{}", HttpStatusCode.OK)
        }
        val client = HttpClient(engine)

        try {
            KtorPicnicHttpTransport(client).execute(
                PicnicHttpRequest("GET", "https://picnic.example.test/api/15/bootstrap", emptyMap())
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun propagatesMockEngineFailure() = runTest {
        val engine = MockEngine { throw TransportFailure("connection unavailable") }
        val client = HttpClient(engine)

        try {
            val failure = assertFailsWith<TransportFailure> {
                KtorPicnicHttpTransport(client).execute(
                    PicnicHttpRequest("GET", "https://picnic.example.test/api/15/bootstrap", emptyMap())
                )
            }
            assertEquals("connection unavailable", failure.message)
        } finally {
            client.close()
        }
    }

    @Test
    fun productionTransportExecutesCurrentFirstReadFallback() = runTest {
        val paths = mutableListOf<String>()
        val engine = MockEngine { request ->
            paths += request.url.encodedPath
            if (request.url.encodedPath.contains("/pages/product-details-page-root")) {
                respond(
                    """{"error":{"code":"NOT_FOUND","message":"Not Found"}}""",
                    HttpStatusCode.NotFound,
                    headersOf("Content-Type", "application/json")
                )
            } else {
                respond(
                    """{"product_details":{"product_id":"s1001","name":"Legacy oats","price":249}}""",
                    HttpStatusCode.OK,
                    headersOf("Content-Type", "application/json")
                )
            }
        }
        val httpClient = HttpClient(engine)

        try {
            val picnic = PicnicClient(
                PicnicClientConfig(baseUrlOverride = "https://picnic.example.test/api/15"),
                KtorPicnicHttpTransport(httpClient)
            )
            val details = picnic.catalog.getProductDetails("s1001")

            assertEquals(PicnicRouteGeneration.LEGACY, details.source.routeGeneration)
            assertEquals("Legacy oats", details.product.name)
            assertEquals(
                listOf(
                    "/api/15/pages/product-details-page-root",
                    "/api/15/product/s1001"
                ),
                paths
            )
            assertTrue(paths.size == 2)
        } finally {
            httpClient.close()
        }
    }
}

private class TransportFailure(message: String) : RuntimeException(message)

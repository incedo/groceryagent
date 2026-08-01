package com.groceryautomate.picnic.adapter.out.http

import com.groceryautomate.picnic.application.port.out.PicnicHttpRequest
import com.groceryautomate.picnic.application.port.out.PicnicHttpResponse
import com.groceryautomate.picnic.application.port.out.PicnicHttpTransport
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpMethod

class KtorPicnicHttpTransport(
    private val client: HttpClient
) : PicnicHttpTransport {
    override suspend fun execute(request: PicnicHttpRequest): PicnicHttpResponse {
        val response = client.request(request.url) {
            method = HttpMethod(request.method)
            headers {
                request.headers.forEach { (name, value) -> append(name, value) }
            }
            request.body?.let(::setBody)
        }
        return PicnicHttpResponse(
            statusCode = response.status.value,
            headers = response.headers.entries().associate { it.key to it.value },
            body = response.bodyAsBytes()
        )
    }
}

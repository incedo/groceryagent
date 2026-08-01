package com.groceryautomate.picnic

import com.groceryautomate.picnic.application.port.out.PicnicHttpRequest
import com.groceryautomate.picnic.application.port.out.PicnicHttpResponse
import com.groceryautomate.picnic.application.port.out.PicnicHttpTransport

internal class RecordingTransport(
    private val responder: (PicnicHttpRequest) -> PicnicHttpResponse = { jsonResponse("{}") }
) : PicnicHttpTransport {
    val requests = mutableListOf<PicnicHttpRequest>()

    override suspend fun execute(request: PicnicHttpRequest): PicnicHttpResponse {
        requests += request
        return responder(request)
    }
}

internal fun jsonResponse(
    body: String,
    status: Int = 200,
    headers: Map<String, List<String>> = emptyMap()
) = PicnicHttpResponse(status, headers, body.encodeToByteArray())

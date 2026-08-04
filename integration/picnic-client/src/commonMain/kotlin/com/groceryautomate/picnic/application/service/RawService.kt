package com.groceryautomate.picnic.application.service

import com.groceryautomate.picnic.adapter.out.http.PicnicRequester
import com.groceryautomate.picnic.application.port.`in`.PicnicRawPort
import com.groceryautomate.picnic.domain.PicnicRequestPolicy
import kotlinx.serialization.json.JsonElement

internal class RawService(
    private val requester: PicnicRequester
) : PicnicRawPort {
    override suspend fun sendRequest(
        method: String,
        path: String,
        data: JsonElement?,
        includePicnicHeaders: Boolean
    ): JsonElement {
        require(method in supportedMethods) { "Unsupported HTTP method: $method" }
        require(path.isNotBlank()) { "Request path must not be blank." }
        return requester.json(
            method,
            path,
            data,
            PicnicRequestPolicy.Storefront.copy(includeDeviceHeaders = includePicnicHeaders)
        )
    }

    private companion object {
        val supportedMethods = setOf("GET", "POST", "PUT", "DELETE")
    }
}

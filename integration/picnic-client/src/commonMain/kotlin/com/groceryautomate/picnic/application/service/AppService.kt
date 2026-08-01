package com.groceryautomate.picnic.application.service

import com.groceryautomate.picnic.adapter.out.http.PicnicRequester
import com.groceryautomate.picnic.adapter.out.http.encodePath
import com.groceryautomate.picnic.application.port.`in`.PicnicAppPort
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class AppService(
    private val requester: PicnicRequester
) : PicnicAppPort {
    override suspend fun getBootstrapData(): JsonElement = requester.json("GET", "/bootstrap")

    override suspend fun getPage(pageId: String): JsonElement {
        require(pageId.isNotBlank()) { "Page id must not be blank." }
        return requester.json("GET", "/pages/${encodePageReference(pageId)}")
    }

    override suspend fun resolveDeeplink(url: String): JsonElement {
        require(url.isNotBlank()) { "Deeplink URL must not be blank." }
        return requester.json(
            "POST",
            "/deeplink/resolve",
            buildJsonObject { put("url", url) }
        )
    }

    private fun encodePageReference(reference: String): String {
        val page = reference.substringBefore('?')
        val query = reference.substringAfter('?', missingDelimiterValue = "")
        if (query.isEmpty()) return encodePath(page)
        return encodePath(page) + "?" + query
    }
}

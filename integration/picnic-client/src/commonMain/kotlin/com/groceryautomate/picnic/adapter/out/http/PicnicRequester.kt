package com.groceryautomate.picnic.adapter.out.http

import com.groceryautomate.picnic.application.port.out.PicnicAuthStore
import com.groceryautomate.picnic.application.port.out.PicnicHttpRequest
import com.groceryautomate.picnic.application.port.out.PicnicHttpResponse
import com.groceryautomate.picnic.application.port.out.PicnicHttpTransport
import com.groceryautomate.picnic.domain.PicnicApiException
import com.groceryautomate.picnic.domain.PicnicClientConfig
import com.groceryautomate.picnic.domain.PicnicRequestPolicy
import com.groceryautomate.picnic.domain.PicnicSessionPolicy
import com.groceryautomate.picnic.domain.baseUrl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

internal class PicnicRequester(
    private val config: PicnicClientConfig,
    private val transport: PicnicHttpTransport,
    private val authStore: PicnicAuthStore,
    private val json: Json
) {
    suspend fun request(
        method: String,
        path: String,
        body: JsonElement? = null,
        policy: PicnicRequestPolicy = PicnicRequestPolicy.Storefront,
        additionalHeaders: Map<String, String> = emptyMap()
    ): PicnicHttpResponse {
        val headers = linkedMapOf(
            "User-Agent" to config.userAgent,
            "Accept-Language" to config.country.language
        )
        if (body != null) headers["Content-Type"] = "application/json; charset=UTF-8"
        if (policy.session == PicnicSessionPolicy.IF_AVAILABLE) {
            authStore.current()?.let { headers["x-picnic-auth"] = it }
        }
        if (policy.includeDeviceHeaders) {
            headers["x-picnic-agent"] = config.agent
            headers["x-picnic-did"] = config.deviceId
        }
        headers.putAll(additionalHeaders)
        val url = if (path.startsWith("http://") || path.startsWith("https://")) {
            path
        } else {
            "${config.baseUrl(policy.destination)}/${path.trimStart('/')}"
        }
        val response = transport.execute(
            PicnicHttpRequest(method, url, headers, body?.toString()?.encodeToByteArray())
        )
        if (response.statusCode !in 200..299) throw response.toApiException()
        return response
    }

    suspend fun json(
        method: String,
        path: String,
        body: JsonElement? = null,
        policy: PicnicRequestPolicy = PicnicRequestPolicy.Storefront,
        additionalHeaders: Map<String, String> = emptyMap()
    ): JsonElement {
        val response = request(method, path, body, policy, additionalHeaders)
        return response.body.takeIf(ByteArray::isNotEmpty)
            ?.decodeToString()
            ?.let(json::parseToJsonElement)
            ?: JsonObject(emptyMap())
    }

    private fun PicnicHttpResponse.toApiException(): PicnicApiException {
        val error = runCatching {
            json.parseToJsonElement(body.decodeToString()).jsonObject["error"]?.jsonObject
        }.getOrNull()
        val providerMessage = (error?.get("message") as? JsonPrimitive)?.content
        val providerCode = (error?.get("code") as? JsonPrimitive)?.content
        return PicnicApiException(
            statusCode = statusCode,
            providerMessage = providerMessage,
            providerCode = providerCode,
            message = providerMessage?.let { "Picnic returned HTTP $statusCode: $it" }
                ?: "Picnic returned HTTP $statusCode."
        )
    }
}

internal fun encodePath(value: String): String = encodeQuery(value)

internal fun encodeQuery(value: String): String = buildString {
    value.encodeToByteArray().forEach { byte ->
        val number = byte.toInt() and 0xff
        val char = number.toChar()
        if (number in 'a'.code..'z'.code || number in 'A'.code..'Z'.code ||
            number in '0'.code..'9'.code || char in "-._~"
        ) {
            append(char)
        } else {
            append('%').append(number.toString(16).uppercase().padStart(2, '0'))
        }
    }
}

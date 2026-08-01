package com.groceryautomate.picnic.application.service

import com.groceryautomate.picnic.adapter.out.http.PicnicRequester
import com.groceryautomate.picnic.adapter.out.http.encodeQuery
import com.groceryautomate.picnic.application.port.`in`.PicnicConsentPort
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal class ConsentService(
    private val requester: PicnicRequester
) : PicnicConsentPort {
    override suspend fun getConsentSettings(general: Boolean): JsonElement =
        requester.json("GET", "/consents${if (general) "/general" else ""}/settings-page")

    override suspend fun setConsentSettings(input: JsonObject): JsonElement =
        requester.json("PUT", "/consents", input)

    override suspend fun getConsents(topics: List<String>, strategy: String): JsonElement {
        require(strategy.isNotBlank()) { "Consent strategy must not be blank." }
        val query = buildList {
            topics.forEach { topic -> add("consent_topics=${encodeQuery(topic)}") }
            add("strategy=${encodeQuery(strategy)}")
        }.joinToString("&")
        return requester.json("GET", "/consents?$query")
    }

    override suspend fun getGeneralConsents(): JsonElement = requester.json("GET", "/consents/general")

    override suspend fun setGeneralConsents(declarations: JsonObject) {
        requester.request("PUT", "/consents/general", declarations)
    }
}

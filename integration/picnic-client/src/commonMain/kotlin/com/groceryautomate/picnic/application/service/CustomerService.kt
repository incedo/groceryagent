package com.groceryautomate.picnic.application.service

import com.groceryautomate.picnic.adapter.out.http.PicnicRequester
import com.groceryautomate.picnic.adapter.out.http.encodeQuery
import com.groceryautomate.picnic.application.port.`in`.PicnicCustomerServicePort
import com.groceryautomate.picnic.domain.PicnicRequestPolicy
import kotlinx.serialization.json.JsonElement

internal class CustomerService(
    private val requester: PicnicRequester
) : PicnicCustomerServicePort {
    override suspend fun getContactInfo(): JsonElement =
        requester.json("GET", "/cs-contact-info")

    override suspend fun getMessages(displayPositions: List<String>?): JsonElement {
        val query = displayPositions.orEmpty().joinToString("&") {
            "display_position=${encodeQuery(it)}"
        }.takeIf(String::isNotEmpty)?.let { "?$it" }.orEmpty()
        return requester.json("GET", "/messages$query")
    }

    override suspend fun getReminders(): JsonElement =
        requester.json("GET", "/reminders")

    override suspend fun setReminders(reminders: JsonElement) {
        requester.request("PUT", "/reminders", reminders)
    }

    override suspend fun getParcels(): JsonElement =
        requester.json("GET", "/parcels")

    override suspend fun getUnauthenticatedContactInfo(countryCode: String): JsonElement {
        require(countryCode.isNotBlank()) { "Country code must not be blank." }
        return requester.json(
            "GET",
            "/cs-contact-info",
            policy = PicnicRequestPolicy.PublicGateway,
            additionalHeaders = mapOf("picnic-country" to countryCode)
        )
    }
}

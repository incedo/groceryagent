package com.groceryautomate.picnic.application.service

import com.groceryautomate.picnic.adapter.out.http.PicnicRequester
import com.groceryautomate.picnic.application.port.`in`.PicnicUserOnboardingPort
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class UserOnboardingService(
    private val requester: PicnicRequester
) : PicnicUserOnboardingPort {
    override suspend fun setHouseholdDetails(details: JsonObject): JsonElement =
        requester.json("POST", "/user-onboarding/household-details", details)

    override suspend fun setBusinessDetails(details: JsonObject): JsonElement =
        requester.json("POST", "/user-onboarding/business-details", details)

    override suspend fun subscribePush(topics: List<String>): JsonElement = requester.json(
        "POST",
        "/user-onboarding/subscribe-push",
        buildJsonObject { put("topics", buildJsonArray { topics.forEach { add(JsonPrimitive(it)) } }) }
    )
}

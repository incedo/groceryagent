package com.groceryautomate.picnic.application.service

import com.groceryautomate.picnic.adapter.out.http.PicnicRequester
import com.groceryautomate.picnic.application.port.`in`.PicnicUserPort
import com.groceryautomate.picnic.domain.PicnicClientConfig
import com.groceryautomate.picnic.domain.PicnicRequestPolicy
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class UserService(
    private val requester: PicnicRequester,
    private val config: PicnicClientConfig
) : PicnicUserPort {
    override suspend fun getUserDetails(): JsonElement = requester.json("GET", "/user")

    override suspend fun getUserInfo(): JsonElement = requester.json("GET", "/user-info")

    override suspend fun getProfileMenu(): JsonElement =
        requester.json("GET", "/profile-menu?fetch_mgm=true")

    override suspend fun submitSuggestion(suggestion: String): JsonElement {
        require(suggestion.isNotBlank()) { "Suggestion must not be blank." }
        return requester.json(
            "POST",
            "/user/suggestion",
            buildJsonObject { put("suggestion", suggestion) }
        )
    }

    override suspend fun registerPushToken(pushDestination: String, pushVersion: Int): JsonElement {
        require(pushDestination.isNotBlank()) { "Push destination must not be blank." }
        require(pushVersion > 0) { "Push version must be positive." }
        return requester.json(
            "POST",
            "/user/device/register_push",
            buildJsonObject {
                put("push_destination", pushDestination)
                put("push_version", pushVersion)
            }
        )
    }

    override suspend fun checkForUpdates(): JsonElement {
        val body = buildJsonObject {
            put("device_id", config.deviceId)
            put("device_name", config.deviceName)
            put("client_id", config.clientId.toString())
            put("version", config.clientVersion)
            put("device_os", config.deviceOs)
            put("build_number", config.buildNumber)
            put("first_time", config.firstInstallUpdateCheck)
            put("tracking", buildJsonObject {
                put("adjust_id", config.tracking.adjustId)
                put("advertiser_id", config.tracking.advertiserId)
                put("tracking_enabled", config.tracking.enabled)
            })
        }
        return currentFirstRead(
            current = { requester.json("POST", "/update_check", body) },
            legacy = {
                requester.json(
                    "POST",
                    "/update_check",
                    body,
                    policy = PicnicRequestPolicy.GatewaySession
                )
            }
        )
    }
}

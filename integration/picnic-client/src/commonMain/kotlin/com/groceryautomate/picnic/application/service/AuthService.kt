package com.groceryautomate.picnic.application.service

import com.groceryautomate.picnic.adapter.out.http.PicnicRequester
import com.groceryautomate.picnic.application.port.`in`.PicnicAuthPort
import com.groceryautomate.picnic.application.port.out.PicnicAuthStore
import com.groceryautomate.picnic.application.port.out.PicnicPasswordHasher
import com.groceryautomate.picnic.domain.PicnicApiException
import com.groceryautomate.picnic.domain.PicnicClientConfig
import com.groceryautomate.picnic.domain.PicnicCompatibilityException
import com.groceryautomate.picnic.domain.PicnicLoginResult
import com.groceryautomate.picnic.domain.PicnicRequestPolicy
import com.groceryautomate.picnic.domain.PicnicRouteGeneration
import com.groceryautomate.picnic.domain.isReadFallbackEligible
import com.groceryautomate.picnic.domain.routeAttempt
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal class AuthService(
    private val requester: PicnicRequester,
    private val config: PicnicClientConfig,
    private val authStore: PicnicAuthStore,
    private val passwordHasher: PicnicPasswordHasher,
    private val json: Json
) : PicnicAuthPort {
    override val authenticated: Boolean get() = authStore.current() != null

    override fun currentAuthKey(): String? = authStore.current()

    override suspend fun login(username: String, password: String): PicnicLoginResult {
        require(username.isNotBlank()) { "Picnic username must not be blank." }
        require(password.isNotEmpty()) { "Picnic password must not be empty." }
        val body = buildJsonObject {
            put("client_id", config.clientId.toString())
            put("client_version", config.clientVersion)
            put("device_id", config.deviceId)
            put("device_name", config.deviceName)
            put("key", username)
            put("secret", passwordHasher.hash(password))
        }
        val (response, generation) = currentFirstLogin(body)
        authStore.replace(response.requiredAuthHeader("Login"))
        val result = json.parseToJsonElement(response.body.decodeToString()).jsonObject
        return PicnicLoginResult(
            userId = result.getValue("user_id").jsonPrimitive.content,
            secondFactorAuthenticationRequired = result.boolean("second_factor_authentication_required"),
            showSecondFactorAuthenticationIntro = result.boolean("show_second_factor_authentication_intro"),
            routeGeneration = generation
        )
    }

    private suspend fun currentFirstLogin(body: kotlinx.serialization.json.JsonElement) = try {
        requester.request("POST", "/user/login", body, PicnicRequestPolicy.GatewayLogin) to
            PicnicRouteGeneration.CURRENT
    } catch (currentFailure: Throwable) {
        if (!currentFailure.isReadFallbackEligible()) throw currentFailure
        try {
            requester.request("POST", "/user/login", body, PicnicRequestPolicy.LegacyStorefrontLogin) to
                PicnicRouteGeneration.LEGACY
        } catch (legacyFailure: Throwable) {
            if (legacyFailure is CancellationException) throw legacyFailure
            throw PicnicCompatibilityException(
                listOf(
                    currentFailure.routeAttempt(PicnicRouteGeneration.CURRENT),
                    legacyFailure.routeAttempt(PicnicRouteGeneration.LEGACY)
                )
            )
        }
    }

    override suspend fun generateSecondFactorCode(channel: String) {
        require(channel.isNotBlank()) { "Picnic 2FA channel must not be blank." }
        requester.request(
            "POST",
            "/user/2fa/generate",
            buildJsonObject { put("channel", channel) }
        )
    }

    override suspend fun verifySecondFactorCode(code: String) {
        require(code.isNotBlank()) { "Picnic 2FA code must not be blank." }
        val response = requester.request(
            "POST",
            "/user/2fa/verify",
            buildJsonObject { put("otp", code) }
        )
        authStore.replace(response.requiredAuthHeader("2FA verification"))
    }

    override suspend fun logout() {
        requester.request("POST", "/user/logout")
        authStore.clear()
    }

    override suspend fun generatePhoneVerificationCode(phoneNumber: String) {
        require(phoneNumber.isNotBlank()) { "Phone number must not be blank." }
        requester.request(
            "POST",
            "/user/phone_verification/generate",
            buildJsonObject { put("phone_number", phoneNumber) }
        )
    }

    override suspend fun verifyPhoneNumber(phoneNumber: String, code: String) {
        require(phoneNumber.isNotBlank()) { "Phone number must not be blank." }
        require(code.isNotBlank()) { "Phone verification code must not be blank." }
        requester.request(
            "POST",
            "/user/phone_verification/verify",
            buildJsonObject {
                put("otp", code)
                put("phone_number", phoneNumber)
            }
        )
    }
}

private fun com.groceryautomate.picnic.application.port.out.PicnicHttpResponse.requiredAuthHeader(
    operation: String
): String = header("x-picnic-auth")?.trim()?.takeIf(String::isNotEmpty)
    ?: throw PicnicApiException(statusCode, null, "$operation failed: no Picnic auth key received.")

private fun kotlinx.serialization.json.JsonObject.boolean(name: String): Boolean =
    this[name]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false

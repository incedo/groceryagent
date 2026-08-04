package com.groceryautomate.picnic.domain

enum class PicnicCountry(val apiCode: String, val language: String) {
    NETHERLANDS("nl", "nl"),
    GERMANY("de", "de"),
    FRANCE("fr", "fr");

    companion object {
        fun fromApiCode(value: String): PicnicCountry = entries.firstOrNull {
            it.apiCode.equals(value.trim(), ignoreCase = true)
        } ?: error("Unsupported Picnic country: $value")
    }
}

enum class PicnicImageSize(val pathValue: String) {
    TINY("tiny"),
    SMALL("small"),
    MEDIUM("medium"),
    LARGE("large"),
    EXTRA_LARGE("extra-large")
}

data class PicnicClientConfig(
    val country: PicnicCountry = PicnicCountry.NETHERLANDS,
    val apiVersion: Int = 15,
    val deviceId: String = "3C417201548B2E3B",
    val clientId: Int = 30100,
    val clientVersion: String = "1.239.3",
    val buildNumber: Int = 15578,
    val deviceName: String = "kotlin-multiplatform-client",
    val deviceOs: String = "kotlin-multiplatform",
    val userAgent: String = "grocery-automate-picnic-client/1",
    val agent: String = "$clientId;$clientVersion-$buildNumber",
    val firstInstallUpdateCheck: Boolean = true,
    val tracking: PicnicTrackingConfig = PicnicTrackingConfig(),
    val baseUrlOverride: String? = null,
    val gatewayApiBaseUrlOverride: String? = null,
    val storefrontApiBaseUrlOverride: String? = null,
    val publicGatewayApiBaseUrlOverride: String? = null,
    val storefrontAssetsBaseUrlOverride: String? = null
) {
    init {
        require(apiVersion > 0) { "Picnic API version must be positive." }
        require(deviceId.isNotBlank()) { "Picnic device id must not be blank." }
        require(agent.isNotBlank()) { "Picnic agent must not be blank." }
        require(clientId > 0) { "Picnic client id must be positive." }
        require(clientVersion.isNotBlank()) { "Picnic client version must not be blank." }
        require(buildNumber > 0) { "Picnic build number must be positive." }
        require(deviceName.isNotBlank()) { "Picnic device name must not be blank." }
        require(deviceOs.isNotBlank()) { "Picnic device OS must not be blank." }
        require(userAgent.isNotBlank()) { "Picnic user agent must not be blank." }
    }

    val gatewayApiBaseUrl: String = normalized(gatewayApiBaseUrlOverride)
        ?: normalized(baseUrlOverride)
        ?: "https://gateway-prod.global.picnicinternational.com/api/$apiVersion"

    val storefrontApiBaseUrl: String = normalized(storefrontApiBaseUrlOverride)
        ?: normalized(baseUrlOverride)
        ?: "https://storefront-prod.${country.apiCode}.picnicinternational.com/api/$apiVersion"

    val publicGatewayApiBaseUrl: String = normalized(publicGatewayApiBaseUrlOverride)
        ?: normalized(baseUrlOverride)?.replace("/api/", "/public-api/")
        ?: "https://gateway-prod.global.picnicinternational.com/public-api/$apiVersion"

    val storefrontAssetsBaseUrl: String = normalized(storefrontAssetsBaseUrlOverride)
        ?: normalized(baseUrlOverride)?.substringBefore("/api/")
        ?: "https://storefront-prod.${country.apiCode}.picnicinternational.com"

    val apiBaseUrl: String get() = storefrontApiBaseUrl
    val storefrontBaseUrl: String get() = storefrontAssetsBaseUrl

    private fun normalized(value: String?): String? = value?.trim()?.trimEnd('/')
        ?.takeIf(String::isNotEmpty)
}

data class PicnicTrackingConfig(
    val adjustId: String = "notAvailable",
    val advertiserId: String = "notAvailable",
    val enabled: Boolean = false
)

data class PicnicLoginResult(
    val userId: String,
    val secondFactorAuthenticationRequired: Boolean,
    val showSecondFactorAuthenticationIntro: Boolean,
    val routeGeneration: PicnicRouteGeneration = PicnicRouteGeneration.CURRENT
)

class PicnicApiException(
    val statusCode: Int,
    val providerMessage: String?,
    message: String,
    val providerCode: String? = null
) : RuntimeException(message)

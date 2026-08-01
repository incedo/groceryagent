package com.groceryautomate.picnic.domain

enum class PicnicDestination {
    GATEWAY_API,
    STOREFRONT_API,
    PUBLIC_GATEWAY_API,
    STOREFRONT_ASSETS
}

enum class PicnicSessionPolicy {
    NONE,
    IF_AVAILABLE
}

data class PicnicRequestPolicy(
    val destination: PicnicDestination,
    val session: PicnicSessionPolicy,
    val includeDeviceHeaders: Boolean
) {
    companion object {
        val Storefront = PicnicRequestPolicy(
            PicnicDestination.STOREFRONT_API,
            PicnicSessionPolicy.IF_AVAILABLE,
            includeDeviceHeaders = true
        )
        val GatewayLogin = PicnicRequestPolicy(
            PicnicDestination.GATEWAY_API,
            PicnicSessionPolicy.NONE,
            includeDeviceHeaders = false
        )
        val LegacyStorefrontLogin = PicnicRequestPolicy(
            PicnicDestination.STOREFRONT_API,
            PicnicSessionPolicy.NONE,
            includeDeviceHeaders = false
        )
        val GatewaySession = PicnicRequestPolicy(
            PicnicDestination.GATEWAY_API,
            PicnicSessionPolicy.IF_AVAILABLE,
            includeDeviceHeaders = true
        )
        val PublicGateway = PicnicRequestPolicy(
            PicnicDestination.PUBLIC_GATEWAY_API,
            PicnicSessionPolicy.NONE,
            includeDeviceHeaders = false
        )
        val StorefrontAsset = PicnicRequestPolicy(
            PicnicDestination.STOREFRONT_ASSETS,
            PicnicSessionPolicy.NONE,
            includeDeviceHeaders = false
        )
    }
}

internal fun PicnicClientConfig.baseUrl(destination: PicnicDestination): String = when (destination) {
    PicnicDestination.GATEWAY_API -> gatewayApiBaseUrl
    PicnicDestination.STOREFRONT_API -> storefrontApiBaseUrl
    PicnicDestination.PUBLIC_GATEWAY_API -> publicGatewayApiBaseUrl
    PicnicDestination.STOREFRONT_ASSETS -> storefrontAssetsBaseUrl
}

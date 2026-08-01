package com.groceryautomate.picnic.application.service

import com.groceryautomate.picnic.adapter.out.http.PicnicRequester
import com.groceryautomate.picnic.adapter.out.http.encodePath
import com.groceryautomate.picnic.adapter.out.http.encodeQuery
import com.groceryautomate.picnic.application.port.`in`.PicnicCatalogPort
import com.groceryautomate.picnic.application.port.out.PicnicClock
import com.groceryautomate.picnic.application.port.out.PicnicIdGenerator
import com.groceryautomate.picnic.domain.PicnicClientConfig
import com.groceryautomate.picnic.domain.PicnicImageSize
import com.groceryautomate.picnic.domain.PicnicMappingException
import com.groceryautomate.picnic.domain.PicnicProviderSource
import com.groceryautomate.picnic.domain.PicnicProductDetails
import com.groceryautomate.picnic.domain.PicnicRequestPolicy
import com.groceryautomate.picnic.domain.PicnicRouteGeneration
import com.groceryautomate.picnic.domain.PicnicSearchRequest
import com.groceryautomate.picnic.domain.PicnicSearchResult
import kotlinx.serialization.json.JsonElement

internal class CatalogService(
    private val requester: PicnicRequester,
    private val config: PicnicClientConfig,
    private val clock: PicnicClock,
    private val idGenerator: PicnicIdGenerator
) : PicnicCatalogPort {
    override suspend fun search(query: String): PicnicSearchResult =
        search(PicnicSearchRequest(query))

    override suspend fun search(request: PicnicSearchRequest): PicnicSearchResult {
        require(request.query.isNotBlank()) { "Search query must not be blank." }
        val sessionId = request.sessionId?.takeIf(String::isNotBlank) ?: idGenerator.newId()
        val pendingId = request.pendingSessionId?.takeIf(String::isNotBlank) ?: idGenerator.newId()
        val currentEndpoint = "/pages/search-page-root-content"
        val query = "?search_term=${encodeQuery(request.query)}" +
            "&search_session_id=${encodeQuery(sessionId)}" +
            "&pending_search_session_id=${encodeQuery(pendingId)}" +
            "&is_search_recommendations_active=${request.recommendationsActive}" +
            "&is_text_input_focused=${request.textInputFocused}" +
            "&force_focus_from_tab=false&skip_initial_search_on_focus=" +
            "&show_dev_chooser=false"
        return currentFirstRead(
            current = {
                val page = requester.json("GET", currentEndpoint + query)
                PicnicSearchResult(
                    request.query,
                    extractProductSummaries(page),
                    source(currentEndpoint, PicnicRouteGeneration.CURRENT)
                )
            },
            legacy = {
                val endpoint = "/search"
                val page = requester.json("GET", "$endpoint?search_term=${encodeQuery(request.query)}")
                PicnicSearchResult(
                    request.query,
                    extractProductSummaries(page),
                    source(endpoint, PicnicRouteGeneration.LEGACY)
                )
            }
        )
    }

    override suspend fun getSuggestions(query: String): JsonElement {
        require(query.isNotBlank()) { "Suggestion query must not be blank." }
        return requester.json("GET", "/suggest?search_term=${encodeQuery(query)}")
    }

    override suspend fun getProductDetailsPage(productId: String): JsonElement {
        requireId(productId, "Product")
        return requester.json(
            "GET",
            "/pages/product-details-page-root?id=${encodeQuery(productId)}" +
                "&show_category_action=true&show_remove_from_purchases_page_action=true"
        )
    }

    override suspend fun getProductDetails(productId: String): PicnicProductDetails {
        requireId(productId, "Product")
        return currentFirstRead(
            current = {
                extractProductDetails(
                    productId,
                    getProductDetailsPage(productId),
                    source("/pages/product-details-page-root", PicnicRouteGeneration.CURRENT)
                ).also { details ->
                    if (details.product.name.isBlank()) {
                        throw PicnicMappingException("Current product response has no product name.")
                    }
                }
            },
            legacy = {
                val endpoint = "/product/${encodePath(productId)}"
                extractLegacyProductDetails(
                    productId,
                    requester.json("GET", endpoint),
                    source(endpoint, PicnicRouteGeneration.LEGACY)
                )
            }
        )
    }

    override suspend fun getImage(imageId: String, size: PicnicImageSize): ByteArray {
        requireId(imageId, "Image")
        return requester.request(
            "GET",
            "/static/images/${encodePath(imageId)}/${size.pathValue}.png",
            policy = PicnicRequestPolicy.StorefrontAsset
        ).body
    }

    override suspend fun getImageAsDataUri(imageId: String, size: PicnicImageSize): String =
        "data:image/png;base64,${encodeBase64(getImage(imageId, size))}"

    private fun requireId(value: String, label: String) {
        require(value.isNotBlank()) { "$label id must not be blank." }
    }

    private fun source(endpoint: String, generation: PicnicRouteGeneration) = PicnicProviderSource(
        endpoint = endpoint,
        countryCode = config.country.apiCode,
        apiVersion = config.apiVersion,
        observedAt = clock.nowIso8601(),
        routeGeneration = generation
    )
}

private val base64Alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

private fun encodeBase64(bytes: ByteArray): String = buildString(((bytes.size + 2) / 3) * 4) {
    var index = 0
    while (index < bytes.size) {
        val first = bytes[index++].toInt() and 0xff
        val second = if (index < bytes.size) bytes[index++].toInt() and 0xff else -1
        val third = if (index < bytes.size) bytes[index++].toInt() and 0xff else -1
        append(base64Alphabet[first ushr 2])
        append(base64Alphabet[((first and 0x03) shl 4) or if (second >= 0) second ushr 4 else 0])
        append(if (second >= 0) base64Alphabet[((second and 0x0f) shl 2) or if (third >= 0) third ushr 6 else 0] else '=')
        append(if (third >= 0) base64Alphabet[third and 0x3f] else '=')
    }
}

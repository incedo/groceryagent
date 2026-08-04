package com.groceryautomate.picnic.adapter.`in`.catalog

import com.groceryautomate.catalog.CatalogProduct
import com.groceryautomate.catalog.ProductCatalogPort
import com.groceryautomate.catalog.ProductId
import com.groceryautomate.catalog.ProductSearchResult
import com.groceryautomate.picnic.application.port.`in`.PicnicCatalogPort
import com.groceryautomate.picnic.domain.PicnicApiException
import com.groceryautomate.picnic.domain.PicnicCompatibilityException
import com.groceryautomate.picnic.domain.PicnicFailureReason
import com.groceryautomate.picnic.domain.PicnicProductDetails
import com.groceryautomate.picnic.domain.PicnicSearchResult

class PicnicCanonicalCatalogAdapter private constructor(
    private val source: PicnicCanonicalCatalogSource
) : ProductCatalogPort {
    constructor(port: PicnicCatalogPort) : this(PicnicPortCatalogSource(port))

    internal constructor(
        search: suspend (String) -> PicnicSearchResult,
        getProduct: suspend (String) -> PicnicProductDetails
    ) : this(FunctionCatalogSource(search, getProduct))

    override suspend fun search(query: String, limit: Int): ProductSearchResult {
        require(query.isNotBlank()) { "Product search query must not be blank." }
        require(limit in 1..100) { "Product search limit must be between 1 and 100." }
        return source.search(query).toCanonical(limit)
    }

    override suspend fun getProduct(id: ProductId): CatalogProduct? = try {
        source.getProduct(id.toPicnicExternalId()).toCanonical()
    } catch (failure: PicnicApiException) {
        if (failure.statusCode in routeUnavailableStatuses) null else throw failure
    } catch (failure: PicnicCompatibilityException) {
        if (failure.attempts.isNotEmpty() && failure.attempts.all {
                it.reason == PicnicFailureReason.ROUTE_UNAVAILABLE
            }
        ) {
            null
        } else {
            throw failure
        }
    }
}

private interface PicnicCanonicalCatalogSource {
    suspend fun search(query: String): PicnicSearchResult
    suspend fun getProduct(productId: String): PicnicProductDetails
}

private class PicnicPortCatalogSource(
    private val port: PicnicCatalogPort
) : PicnicCanonicalCatalogSource {
    override suspend fun search(query: String): PicnicSearchResult = port.search(query)
    override suspend fun getProduct(productId: String): PicnicProductDetails =
        port.getProductDetails(productId)
}

private class FunctionCatalogSource(
    private val search: suspend (String) -> PicnicSearchResult,
    private val getProduct: suspend (String) -> PicnicProductDetails
) : PicnicCanonicalCatalogSource {
    override suspend fun search(query: String): PicnicSearchResult = search.invoke(query)
    override suspend fun getProduct(productId: String): PicnicProductDetails = getProduct.invoke(productId)
}

private val routeUnavailableStatuses = setOf(404, 405, 410)

private fun ProductId.toPicnicExternalId(): String {
    val parts = value.split(':', limit = 3)
    if (parts.size == 1) return value
    require(parts.size == 3 && parts[0] == "picnic" && parts[2].isNotBlank()) {
        "Product id is not a Picnic canonical id."
    }
    return parts[2]
}

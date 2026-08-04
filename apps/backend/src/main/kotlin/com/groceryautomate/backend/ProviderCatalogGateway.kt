package com.groceryautomate.backend

import com.groceryautomate.catalog.CatalogProduct
import com.groceryautomate.catalog.ProductCatalogPort
import com.groceryautomate.catalog.ProductId
import com.groceryautomate.catalog.ProductSearchResult
import kotlinx.coroutines.CancellationException

class ProviderCatalogGateway(
    private val provider: ProductCatalogPort?
) : ProductCatalogPort {
    override suspend fun search(query: String, limit: Int): ProductSearchResult = providerCall {
        requireProvider().search(query, limit)
    }

    override suspend fun getProduct(id: ProductId): CatalogProduct? = providerCall {
        requireProvider().getProduct(id)
    }

    private fun requireProvider(): ProductCatalogPort = provider
        ?: throw CatalogProviderUnavailable("Catalog provider is not configured.")

    private suspend fun <T> providerCall(block: suspend () -> T): T = try {
        block()
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: CatalogProviderUnavailable) {
        throw failure
    } catch (failure: Throwable) {
        throw CatalogProviderUnavailable("Catalog provider request failed.", failure)
    }
}

class CatalogProviderUnavailable(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

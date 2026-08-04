package com.groceryautomate.catalog

interface ProductCatalogPort {
    suspend fun search(query: String, limit: Int = 20): ProductSearchResult
    suspend fun getProduct(id: ProductId): CatalogProduct?
}

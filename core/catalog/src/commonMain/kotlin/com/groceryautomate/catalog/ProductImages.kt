package com.groceryautomate.catalog

import kotlinx.serialization.Serializable

@Serializable
enum class ProductImageVariant(val pathValue: String) {
    LARGE("large")
}

@Serializable
data class ProductImageAsset(
    val productId: ProductId,
    val provider: String,
    val sourceImageId: String,
    val variant: ProductImageVariant,
    val bucket: String,
    val objectKey: String,
    val publicUrl: String,
    val mediaType: String,
    val byteSize: Long,
    val sha256: String,
    val observedAt: String
) {
    init {
        require(provider.isNotBlank()) { "Image provider must not be blank." }
        require(sourceImageId.isNotBlank()) { "Source image id must not be blank." }
        require(bucket.isNotBlank()) { "Image bucket must not be blank." }
        require(objectKey.isNotBlank()) { "Image object key must not be blank." }
        require(publicUrl.startsWith("https://")) { "Public image URL must use HTTPS." }
        require(mediaType == "image/png") { "Only PNG product images are supported." }
        require(byteSize > 0) { "Image byte size must be positive." }
        require(sha256.length == 64 && sha256.all { it in '0'..'9' || it in 'a'..'f' }) {
            "Image SHA-256 must be 64 lowercase hexadecimal characters."
        }
        require(observedAt.isNotBlank()) { "Image observation time must not be blank." }
    }
}

@Serializable
data class ProductImageImportCandidate(
    val productId: ProductId,
    val sourceImageId: String
) {
    init {
        require(sourceImageId.isNotBlank()) { "Source image id must not be blank." }
    }
}

interface ProductImageAssetPort {
    suspend fun findImageImportCandidates(
        variant: ProductImageVariant,
        limit: Int
    ): List<ProductImageImportCandidate>

    suspend fun getProductImageAsset(
        productId: ProductId,
        variant: ProductImageVariant
    ): ProductImageAsset?
}

data class ProductImageObject(
    val bucket: String,
    val objectKey: String,
    val publicUrl: String
)

interface ProductImageObjectStore {
    suspend fun putPng(sha256: String, bytes: ByteArray): ProductImageObject
}

interface ProductImageSource {
    suspend fun getPng(sourceImageId: String, variant: ProductImageVariant): ByteArray
}

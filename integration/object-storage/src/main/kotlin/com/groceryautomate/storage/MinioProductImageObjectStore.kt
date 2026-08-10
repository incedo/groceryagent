package com.groceryautomate.storage

import com.groceryautomate.catalog.ProductImageObject
import com.groceryautomate.catalog.ProductImageObjectStore
import io.minio.MinioClient
import io.minio.PutObjectArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

data class ProductImageStorageSettings(
    val endpoint: String,
    val accessKey: String,
    val secretKey: String,
    val bucket: String,
    val publicBaseUrl: String
) {
    init {
        require(endpoint.startsWith("https://")) { "S3 endpoint must use HTTPS." }
        require(accessKey.isNotBlank()) { "S3 access key must not be blank." }
        require(secretKey.isNotBlank()) { "S3 secret key must not be blank." }
        require(bucket.isNotBlank()) { "S3 bucket must not be blank." }
        require(publicBaseUrl.startsWith("https://")) { "Asset base URL must use HTTPS." }
    }
}

class MinioProductImageObjectStore(
    private val client: ProductImageObjectWriter,
    private val settings: ProductImageStorageSettings
) : ProductImageObjectStore {
    constructor(settings: ProductImageStorageSettings) : this(
        MinioObjectWriter(
            MinioClient.builder()
                .endpoint(settings.endpoint)
                .credentials(settings.accessKey, settings.secretKey)
                .build()
        ),
        settings
    )

    override suspend fun putPng(sha256: String, bytes: ByteArray): ProductImageObject {
        require(sha256.length == 64 && sha256.all { it in '0'..'9' || it in 'a'..'f' }) {
            "Image SHA-256 must be lowercase hexadecimal."
        }
        require(bytes.isNotEmpty()) { "Image bytes must not be empty." }
        val objectKey = "images/sha256/${sha256.take(2)}/$sha256.png"
        client.put(settings.bucket, objectKey, bytes, "image/png")
        return ProductImageObject(
            bucket = settings.bucket,
            objectKey = objectKey,
            publicUrl = "${settings.publicBaseUrl.trimEnd('/')}/${settings.bucket}/$objectKey"
        )
    }
}

fun interface ProductImageObjectWriter {
    suspend fun put(bucket: String, objectKey: String, bytes: ByteArray, mediaType: String)
}

private class MinioObjectWriter(
    private val client: MinioClient
) : ProductImageObjectWriter {
    override suspend fun put(
        bucket: String,
        objectKey: String,
        bytes: ByteArray,
        mediaType: String
    ) = withContext(Dispatchers.IO) {
        ByteArrayInputStream(bytes).use { stream ->
            client.putObject(
                PutObjectArgs.builder()
                    .bucket(bucket)
                    .`object`(objectKey)
                    .contentType(mediaType)
                    .stream(stream, bytes.size.toLong(), -1)
                    .build()
            )
        }
        Unit
    }
}

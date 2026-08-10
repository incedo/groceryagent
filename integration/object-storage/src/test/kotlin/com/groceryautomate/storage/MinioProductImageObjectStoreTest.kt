package com.groceryautomate.storage

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class MinioProductImageObjectStoreTest {
    @Test
    fun storesPngAtContentAddressedPublicPath() = runTest {
        var write: Write? = null
        val store = MinioProductImageObjectStore(
            ProductImageObjectWriter { bucket, key, bytes, mediaType ->
                write = Write(bucket, key, bytes, mediaType)
            },
            ProductImageStorageSettings(
                "https://minio.example.test",
                "us-east-1",
                "access",
                "secret",
                "product-images",
                "https://assets.example.test/"
            )
        )
        val digest = "ab" + "1".repeat(62)

        val result = store.putPng(digest, byteArrayOf(1, 2, 3))

        assertEquals("product-images", write?.bucket)
        assertEquals("images/sha256/ab/$digest.png", write?.key)
        assertContentEquals(byteArrayOf(1, 2, 3), write?.bytes)
        assertEquals("image/png", write?.mediaType)
        assertEquals(
            "https://assets.example.test/product-images/images/sha256/ab/$digest.png",
            result.publicUrl
        )
    }
}

private data class Write(
    val bucket: String,
    val key: String,
    val bytes: ByteArray,
    val mediaType: String
)

package com.groceryautomate.storage

import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class S3SignedHttpObjectWriterTest {
    @Test
    fun signsPutWithDeterministicAwsV4Headers() {
        val digest = "ab" + "1".repeat(62)
        val headers = S3V4Signer("test-access", "test-secret", "us-east-1").signPut(
            "https://minio.example.test/grocery-product-images/images/sha256/ab/$digest.png",
            "image/png",
            "png-bytes".encodeToByteArray(),
            Instant.parse("2024-08-10T12:00:00Z")
        )

        assertEquals("20240810T120000Z", headers["x-amz-date"])
        assertEquals(
            "ea80334363eed145dfeee51ebae7dc3f1cd7d0c7879f8bfd2070c061d3c33f56",
            headers["x-amz-content-sha256"]
        )
        assertEquals(
            "AWS4-HMAC-SHA256 Credential=test-access/20240810/us-east-1/s3/aws4_request, " +
                "SignedHeaders=content-type;host;x-amz-content-sha256;x-amz-date, " +
                "Signature=303c1ad190382dc9edb9d0ab307cec4de83db83fff88f9de386e17784c480948",
            headers["Authorization"]
        )
    }

    @Test
    fun sendsSignedBytesAndRejectsNonSuccess() = runTest {
        var captured: CapturedPut? = null
        val writer = S3SignedHttpObjectWriter(
            settings(),
            S3HttpTransport { url, headers, bytes ->
                captured = CapturedPut(url, headers, bytes)
                200
            },
            now = { Instant.parse("2024-08-10T12:00:00Z") }
        )

        writer.put("grocery-product-images", "images/test.png", byteArrayOf(1, 2), "image/png")

        assertEquals(
            "https://minio.example.test/grocery-product-images/images/test.png",
            captured?.url
        )
        assertContentEquals(byteArrayOf(1, 2), captured?.bytes)
        assertEquals("image/png", captured?.headers?.get("Content-Type"))

        val failing = S3SignedHttpObjectWriter(
            settings(),
            S3HttpTransport { _, _, _ -> 403 },
            now = { Instant.parse("2024-08-10T12:00:00Z") }
        )
        assertFailsWith<S3UploadException> {
            failing.put("grocery-product-images", "images/test.png", byteArrayOf(1), "image/png")
        }
    }

    @Test
    fun liveMinioPutIsOptIn() = runTest {
        if (System.getenv("RUN_LIVE_S3_TEST") != "true") return@runTest
        val environment = System.getenv()
        val store = MinioProductImageObjectStore(
            ProductImageStorageSettings(
                endpoint = requireNotNull(environment["S3_ENDPOINT"]),
                region = requireNotNull(environment["S3_REGION"]),
                accessKey = requireNotNull(environment["S3_ACCESS_KEY"]),
                secretKey = requireNotNull(environment["S3_SECRET_KEY"]),
                bucket = requireNotNull(environment["S3_BUCKET"]),
                publicBaseUrl = requireNotNull(environment["ASSET_BASE_URL"])
            )
        )

        val result = store.putPng(
            "463a6551eac5bbd4e9e2d4a1ab6175cce775249d0cfa47d482d987732e64950f",
            "native-s3-live-test".encodeToByteArray()
        )

        assertEquals(
            "images/sha256/46/463a6551eac5bbd4e9e2d4a1ab6175cce775249d0cfa47d482d987732e64950f.png",
            result.objectKey
        )
    }
}

private fun settings() = ProductImageStorageSettings(
    endpoint = "https://minio.example.test",
    region = "us-east-1",
    accessKey = "test-access",
    secretKey = "test-secret",
    bucket = "grocery-product-images",
    publicBaseUrl = "https://assets.example.test"
)

private data class CapturedPut(
    val url: String,
    val headers: Map<String, String>,
    val bytes: ByteArray
)

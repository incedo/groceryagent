package com.groceryautomate.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal class S3SignedHttpObjectWriter(
    private val settings: ProductImageStorageSettings,
    private val transport: S3HttpTransport = JavaS3HttpTransport(),
    private val now: () -> Instant = Instant::now
) : ProductImageObjectWriter {
    override suspend fun put(
        bucket: String,
        objectKey: String,
        bytes: ByteArray,
        mediaType: String
    ) {
        val url = "${settings.endpoint.trimEnd('/')}/$bucket/$objectKey"
        val headers = S3V4Signer(
            settings.accessKey,
            settings.secretKey,
            settings.region
        ).signPut(url, mediaType, bytes, now())
        val status = transport.put(url, headers, bytes)
        if (status !in 200..299) throw S3UploadException(status)
    }
}

internal fun interface S3HttpTransport {
    suspend fun put(url: String, headers: Map<String, String>, bytes: ByteArray): Int
}

private class JavaS3HttpTransport(
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build()
) : S3HttpTransport {
    override suspend fun put(url: String, headers: Map<String, String>, bytes: ByteArray): Int =
        withContext(Dispatchers.IO) {
            val request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .also { builder -> headers.forEach(builder::header) }
                .build()
            client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
        }
}

internal class S3V4Signer(
    private val accessKey: String,
    private val secretKey: String,
    private val region: String
) {
    fun signPut(url: String, mediaType: String, bytes: ByteArray, instant: Instant): Map<String, String> {
        val uri = URI.create(url)
        val amzDate = AMZ_DATE.format(instant)
        val date = DATE.format(instant)
        val payloadHash = sha256(bytes)
        val canonicalHeaders = buildString {
            append("content-type:$mediaType\n")
            append("host:${uri.rawAuthority}\n")
            append("x-amz-content-sha256:$payloadHash\n")
            append("x-amz-date:$amzDate\n")
        }
        val canonicalRequest = listOf(
            "PUT",
            uri.rawPath,
            "",
            canonicalHeaders,
            SIGNED_HEADERS,
            payloadHash
        ).joinToString("\n")
        val scope = "$date/$region/s3/aws4_request"
        val stringToSign = "AWS4-HMAC-SHA256\n$amzDate\n$scope\n${sha256(canonicalRequest.encodeToByteArray())}"
        val signature = hmac(signingKey(date), stringToSign).toHex()
        return linkedMapOf(
            "Content-Type" to mediaType,
            "x-amz-content-sha256" to payloadHash,
            "x-amz-date" to amzDate,
            "Authorization" to "AWS4-HMAC-SHA256 Credential=$accessKey/$scope, " +
                "SignedHeaders=$SIGNED_HEADERS, Signature=$signature"
        )
    }

    private fun signingKey(date: String): ByteArray {
        val dateKey = hmac("AWS4$secretKey".encodeToByteArray(), date)
        val regionKey = hmac(dateKey, region)
        val serviceKey = hmac(regionKey, "s3")
        return hmac(serviceKey, "aws4_request")
    }
}

internal class S3UploadException(status: Int) :
    IllegalStateException("S3 upload returned HTTP $status.")

private fun sha256(value: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(value).toHex()

private fun hmac(key: ByteArray, value: String): ByteArray =
    Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal(value.encodeToByteArray())
    }

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

private val AMZ_DATE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
private val DATE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)
private const val SIGNED_HEADERS = "content-type;host;x-amz-content-sha256;x-amz-date"

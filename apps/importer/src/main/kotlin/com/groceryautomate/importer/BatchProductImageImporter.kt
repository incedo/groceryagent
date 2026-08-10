package com.groceryautomate.importer

import com.groceryautomate.catalog.ProductImageAsset
import com.groceryautomate.catalog.ProductImageAssetPort
import com.groceryautomate.catalog.ProductImageObjectStore
import com.groceryautomate.catalog.ProductImageSource
import com.groceryautomate.catalog.ProductImageVariant
import com.groceryautomate.events.ProducerId
import com.groceryautomate.events.ProductImageImportService
import kotlinx.coroutines.CancellationException
import java.security.MessageDigest

class BatchProductImageImporter(
    private val source: ProductImageSource,
    private val objectStore: ProductImageObjectStore,
    private val assets: ProductImageAssetPort,
    private val events: ProductImageImportService,
    private val now: () -> String,
    private val awaitNextImage: suspend () -> Unit = {},
    private val classifyFailure: (Throwable) -> ImportFailureDiagnostic = ::unexpectedFailureDiagnostic
) {
    suspend fun run(limit: Int): ProductImageImportReport {
        val candidates = assets.findImageImportCandidates(ProductImageVariant.LARGE, limit)
        val results = buildList {
            candidates.forEachIndexed { index, candidate ->
                add(
                    try {
                        val bytes = source.getPng(candidate.sourceImageId, ProductImageVariant.LARGE)
                        validatePng(bytes)
                        val sha256 = bytes.sha256()
                        val stored = objectStore.putPng(sha256, bytes)
                        val asset = ProductImageAsset(
                            productId = candidate.productId,
                            provider = "picnic",
                            sourceImageId = candidate.sourceImageId,
                            variant = ProductImageVariant.LARGE,
                            bucket = stored.bucket,
                            objectKey = stored.objectKey,
                            publicUrl = stored.publicUrl,
                            mediaType = PNG_MEDIA_TYPE,
                            byteSize = bytes.size.toLong(),
                            sha256 = sha256,
                            observedAt = now()
                        )
                        val append = events.record(
                            asset,
                            productImageCommandId(
                                candidate.productId.value,
                                candidate.sourceImageId,
                                ProductImageVariant.LARGE.name,
                                sha256
                            ),
                            ProducerId("catalog-image-importer")
                        )
                        ProductImageImportResult(
                            candidate.productId.value,
                            candidate.sourceImageId,
                            if (append.duplicateCommand) ImageImportStatus.ALREADY_STORED else ImageImportStatus.STORED,
                            append.eventCount
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        ProductImageImportResult(
                            candidate.productId.value,
                            candidate.sourceImageId,
                            ImageImportStatus.FAILED,
                            failure = classifyFailure(failure)
                        )
                    }
                )
                if (index < candidates.lastIndex) awaitNextImage()
            }
        }
        return ProductImageImportReport(results)
    }

    private fun validatePng(bytes: ByteArray) {
        require(bytes.isNotEmpty()) { "Image response must not be empty." }
        require(bytes.size <= MAX_IMAGE_BYTES) { "Image response exceeds the size limit." }
        require(bytes.size >= PNG_SIGNATURE.size && bytes.take(PNG_SIGNATURE.size) == PNG_SIGNATURE) {
            "Image response does not have a PNG signature."
        }
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this).joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
        const val PNG_MEDIA_TYPE = "image/png"
        val PNG_SIGNATURE = listOf(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
            .map(Int::toByte)
    }
}

data class ProductImageImportReport(val results: List<ProductImageImportResult>) {
    val failureCount: Int = results.count { it.status == ImageImportStatus.FAILED }
    val successful: Boolean = failureCount == 0
}

data class ProductImageImportResult(
    val productId: String,
    val sourceImageId: String,
    val status: ImageImportStatus,
    val eventCount: Int = 0,
    val failure: ImportFailureDiagnostic? = null
)

enum class ImageImportStatus {
    STORED,
    ALREADY_STORED,
    FAILED
}

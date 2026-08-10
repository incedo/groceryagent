package com.groceryautomate.importer

import com.groceryautomate.catalog.HistoricalProductReference
import com.groceryautomate.catalog.ProductId
import com.groceryautomate.events.ProducerId
import com.groceryautomate.events.ProductReplacementImportResult
import com.groceryautomate.events.ProductReplacementImportService
import com.groceryautomate.events.ProductReplacementImportStatus
import kotlinx.coroutines.CancellationException

class BatchProductReplacementImporter(
    private val imports: ProductReplacementImportService,
    private val awaitNextProduct: suspend () -> Unit = {},
    private val classifyFailure: (Throwable) -> ImportFailureDiagnostic = ::unexpectedFailureDiagnostic
) {
    suspend fun run(manifest: ImportManifest): ProductReplacementImportReport {
        val producerId = ProducerId(manifest.producerId)
        val results = buildList {
            manifest.products.forEachIndexed { index, product ->
                product.requireHistoricalReference()
                val result = try {
                    imports.importReplacement(
                        HistoricalProductReference(
                            ProductId("${product.retailer.providerId}:nl:${product.productId}"),
                            requireNotNull(product.historicalName),
                            requireNotNull(product.historicalUnitQuantity)
                        ),
                        importCommandId(manifest.batchId, product),
                        producerId
                    ).toItemResult()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    ProductReplacementItemResult(
                        product.productId,
                        status = ProductReplacementImportStatus.FAILED,
                        failure = classifyFailure(failure)
                    )
                }
                add(result)
                if (index < manifest.products.lastIndex) awaitNextProduct()
            }
        }
        return ProductReplacementImportReport(manifest.batchId, results)
    }
}

data class ProductReplacementImportReport(
    val batchId: String,
    val results: List<ProductReplacementItemResult>
) {
    val failureCount: Int = results.count { !it.status.isSuccessful }
    val successful: Boolean = failureCount == 0
}

data class ProductReplacementItemResult(
    val previousProductId: String,
    val currentProductId: String? = null,
    val status: ProductReplacementImportStatus,
    val eventCount: Int = 0,
    val candidateIds: List<String> = emptyList(),
    val failure: ImportFailureDiagnostic? = null
)

internal fun ProductReplacementItemResult.toLogLine(): String = buildString {
    append("previous_product=").append(previousProductId)
    append(" status=").append(status)
    currentProductId?.let { append(" current_product=").append(it) }
    append(" events=").append(eventCount)
    if (candidateIds.isNotEmpty()) append(" candidates=").append(candidateIds.joinToString(","))
    failure?.let(::appendDiagnostic)
}

private fun ProductReplacementImportResult.toItemResult() = ProductReplacementItemResult(
    previousProductId = previousProductId.value.substringAfterLast(':'),
    currentProductId = currentProductId?.value,
    status = status,
    eventCount = eventCount,
    candidateIds = candidateIds.map(ProductId::value)
)

private val ProductReplacementImportStatus.isSuccessful: Boolean
    get() = this == ProductReplacementImportStatus.IMPORTED ||
        this == ProductReplacementImportStatus.LINKED_EXISTING ||
        this == ProductReplacementImportStatus.ALREADY_IMPORTED ||
        this == ProductReplacementImportStatus.ALREADY_LINKED

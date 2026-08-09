package com.groceryautomate.importer

import com.groceryautomate.catalog.ProductId
import com.groceryautomate.events.ProducerId
import com.groceryautomate.events.ProductImportService
import kotlinx.coroutines.CancellationException

class BatchProductImporter(
    private val imports: ProductImportService,
    private val historicalPrices: BatchHistoricalPriceImporter? = null,
    private val awaitNextProduct: suspend () -> Unit = {}
) {
    suspend fun run(manifest: ImportManifest): ImportReport {
        val producerId = ProducerId(manifest.producerId)
        val results = buildList {
            manifest.products.forEachIndexed { index, product ->
                val result = try {
                    val append = imports.importProduct(
                        ProductId(product.productId),
                        importCommandId(manifest.batchId, product),
                        producerId
                    )
                    when {
                        append == null -> ImportItemResult(product.productId, ImportStatus.NOT_FOUND)
                        append.duplicateCommand -> ImportItemResult(
                            product.productId,
                            ImportStatus.ALREADY_IMPORTED,
                            append.eventCount
                        )
                        else -> ImportItemResult(product.productId, ImportStatus.IMPORTED, append.eventCount)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    ImportItemResult(product.productId, ImportStatus.FAILED)
                }
                add(result)
                if (index < manifest.products.lastIndex) awaitNextProduct()
            }
        }
        val priceResults = if (manifest.historicalPrices.isEmpty()) emptyList() else
            requireNotNull(historicalPrices) {
                "Historical price import service is required for manifests with price history."
            }.run(manifest)
        return ImportReport(manifest.batchId, results, priceResults)
    }
}

data class ImportReport(
    val batchId: String,
    val results: List<ImportItemResult>,
    val historicalPriceResults: List<HistoricalPriceResult> = emptyList()
) {
    val failureCount: Int = (results.map { it.status } + historicalPriceResults.map { it.status })
        .count { it == ImportStatus.FAILED || it == ImportStatus.NOT_FOUND }
    val successful: Boolean = failureCount == 0
}

data class HistoricalPriceResult(val observationId: String, val status: ImportStatus)

data class ImportItemResult(
    val productId: String,
    val status: ImportStatus,
    val eventCount: Int = 0
)

enum class ImportStatus {
    IMPORTED,
    ALREADY_IMPORTED,
    NOT_FOUND,
    FAILED
}

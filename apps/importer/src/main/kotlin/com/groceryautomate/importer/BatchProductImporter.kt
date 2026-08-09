package com.groceryautomate.importer

import com.groceryautomate.catalog.ProductId
import com.groceryautomate.events.ProducerId
import com.groceryautomate.events.ProductImportService
import kotlinx.coroutines.CancellationException

class BatchProductImporter(
    private val imports: ProductImportService
) {
    suspend fun run(manifest: ImportManifest): ImportReport {
        val producerId = ProducerId(manifest.producerId)
        val results = manifest.products.map { product ->
            try {
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
        }
        return ImportReport(manifest.batchId, results)
    }
}

data class ImportReport(
    val batchId: String,
    val results: List<ImportItemResult>
) {
    val failureCount: Int = results.count { it.status == ImportStatus.FAILED || it.status == ImportStatus.NOT_FOUND }
    val successful: Boolean = failureCount == 0
}

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

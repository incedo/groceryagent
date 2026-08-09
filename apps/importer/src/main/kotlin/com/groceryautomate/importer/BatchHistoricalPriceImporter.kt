package com.groceryautomate.importer

import com.groceryautomate.events.HistoricalPriceImportService
import com.groceryautomate.events.ProducerId
import kotlinx.coroutines.CancellationException

class BatchHistoricalPriceImporter(
    private val imports: HistoricalPriceImportService
) {
    suspend fun run(manifest: ImportManifest): List<HistoricalPriceResult> {
        val producerId = ProducerId(manifest.producerId)
        return manifest.historicalPrices.map { observation ->
            try {
                val append = imports.record(observation, historicalPriceCommandId(observation), producerId)
                HistoricalPriceResult(
                    observation.id.value,
                    if (append.duplicateCommand) ImportStatus.ALREADY_IMPORTED else ImportStatus.IMPORTED
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                HistoricalPriceResult(observation.id.value, ImportStatus.FAILED)
            }
        }
    }
}

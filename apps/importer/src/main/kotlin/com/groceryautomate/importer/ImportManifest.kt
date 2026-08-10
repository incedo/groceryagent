package com.groceryautomate.importer

import com.groceryautomate.catalog.HistoricalPriceObservation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

const val IMPORT_MANIFEST_SCHEMA_VERSION = 1

@Serializable
data class ImportManifest(
    val schemaVersion: Int,
    val batchId: String,
    val producerId: String,
    val products: List<ImportProduct>,
    val historicalPrices: List<HistoricalPriceObservation> = emptyList()
) {
    init {
        require(schemaVersion == IMPORT_MANIFEST_SCHEMA_VERSION) {
            "Unsupported import manifest schema version: $schemaVersion."
        }
        require(batchId.isNotBlank()) { "Import batch id must not be blank." }
        require(producerId.isNotBlank()) { "Import producer id must not be blank." }
        require(products.isNotEmpty()) { "Import manifest must contain at least one product." }
        require(products.distinctBy { it.retailer to it.productId }.size == products.size) {
            "Import manifest must not contain duplicate retailer product ids."
        }
        require(historicalPrices.distinctBy { it.id }.size == historicalPrices.size) {
            "Import manifest must not contain duplicate historical price observations."
        }
    }
}

@Serializable
data class ImportProduct(
    val retailer: ImportRetailer,
    val productId: String,
    val historicalName: String? = null,
    val historicalUnitQuantity: String? = null,
    val historicalImageId: String? = null
) {
    init {
        require(productId.isNotBlank()) { "Provider product id must not be blank." }
        require(historicalName == null || historicalName.isNotBlank()) {
            "Historical product name must be null or non-blank."
        }
        require(historicalUnitQuantity == null || historicalUnitQuantity.isNotBlank()) {
            "Historical unit quantity must be null or non-blank."
        }
        require(historicalImageId == null || historicalImageId.isNotBlank()) {
            "Historical image id must be null or non-blank."
        }
        require((historicalName == null) == (historicalUnitQuantity == null)) {
            "Historical product name and unit quantity must be provided together."
        }
    }

    fun requireHistoricalReference() {
        requireNotNull(historicalName) { "Search replacement import requires a historical product name." }
        requireNotNull(historicalUnitQuantity) {
            "Search replacement import requires a historical unit quantity."
        }
    }
}

@Serializable
enum class ImportRetailer(val providerId: String) {
    @SerialName("picnic")
    PICNIC("picnic")
}

object ImportManifestFile {
    private val json = Json { explicitNulls = false }

    fun read(path: Path): ImportManifest {
        require(Files.isRegularFile(path)) { "Import manifest file does not exist." }
        return json.decodeFromString(Files.readString(path))
    }
}

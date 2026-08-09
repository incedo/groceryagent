package com.groceryautomate.importer

import com.groceryautomate.picnic.domain.PicnicOrderReferenceExtractor
import com.groceryautomate.picnic.domain.PicnicHistoricalPriceExtractor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions

class OrderCaptureFiles(
    private val json: Json = Json { prettyPrint = true }
) {
    fun createCaptureDirectory(path: Path) {
        requirePrivateLocation(path)
        require(!Files.exists(path)) { "Order capture directory already exists." }
        path.parent?.let(Files::createDirectories)
        Files.createDirectory(path)
        setOwnerOnly(path, "rwx------")
    }

    fun writeJson(path: Path, content: JsonElement) {
        Files.writeString(
            path,
            json.encodeToString(JsonElement.serializer(), content),
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE
        )
        setOwnerOnly(path, "rw-------")
    }

    fun writeManifest(path: Path, manifest: ImportManifest) {
        requirePrivateLocation(path)
        require(!Files.exists(path)) { "Import manifest already exists." }
        path.parent?.let(Files::createDirectories)
        Files.writeString(
            path,
            json.encodeToString(manifest),
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE
        )
        setOwnerOnly(path, "rw-------")
    }

    fun readDeliveryDetails(captureDirectory: Path): List<JsonElement> {
        require(Files.isDirectory(captureDirectory)) { "Order capture directory does not exist." }
        require(Files.isRegularFile(captureDirectory.resolve("capture-complete.json"))) {
            "Order capture is incomplete; no import manifest may be generated."
        }
        val files = Files.list(captureDirectory).use { paths ->
            paths.filter { it.fileName.toString().matches(Regex("delivery-[0-9]{3}\\.json")) }
                .sorted()
                .toList()
        }
        return files.map { file -> json.parseToJsonElement(Files.readString(file)) }
    }

    fun markCaptureComplete(directory: Path, deliveryCount: Int) {
        writeJson(
            directory.resolve("capture-complete.json"),
            kotlinx.serialization.json.buildJsonObject { put("deliveryCount", deliveryCount) }
        )
    }

    fun toManifest(captureDirectory: Path, output: Path, batchId: String): ImportManifest {
        val details = readDeliveryDetails(captureDirectory)
        val productIds = PicnicOrderReferenceExtractor.productIds(details)
        require(productIds.isNotEmpty()) { "Order capture contains no recognized Picnic product ids." }
        return ImportManifest(
            schemaVersion = IMPORT_MANIFEST_SCHEMA_VERSION,
            batchId = batchId,
            producerId = "picnic-order-capture",
            products = productIds.map { ImportProduct(ImportRetailer.PICNIC, it) },
            historicalPrices = PicnicHistoricalPriceExtractor.observations(details, ::historicalObservationId)
        ).also { writeManifest(output, it) }
    }

    private fun setOwnerOnly(path: Path, permissions: String) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions))
        } catch (_: UnsupportedOperationException) {
            // The local filesystem does not expose POSIX permissions.
        }
    }

    private fun requirePrivateLocation(path: Path) {
        require(path.isAbsolute || path.normalize().startsWith(Path.of(".local"))) {
            "Repository-local private files must be written below .local/."
        }
    }
}

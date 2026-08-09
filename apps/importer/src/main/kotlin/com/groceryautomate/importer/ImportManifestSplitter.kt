package com.groceryautomate.importer

import com.groceryautomate.catalog.HistoricalPriceObservation
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest

const val IMPORT_MANIFEST_INDEX_SCHEMA_VERSION = 1
const val DEFAULT_MAX_MANIFEST_BYTES = 900_000

@Serializable
data class ImportManifestIndex(
    val schemaVersion: Int,
    val sourceManifestFile: String,
    val sourceBatchId: String,
    val sourceSha256: String,
    val maxProductsPerShard: Int,
    val maxBytesPerShard: Int,
    val totalProductCount: Int,
    val totalHistoricalPriceCount: Int,
    val shards: List<ImportManifestShard>
)

@Serializable
data class ImportManifestShard(
    val sequence: Int,
    val fileName: String,
    val batchId: String,
    val productCount: Int,
    val historicalPriceCount: Int,
    val sha256: String
)

class ImportManifestSplitter(
    private val json: Json = Json { prettyPrint = true; explicitNulls = false }
) {
    fun split(
        sourceFile: Path,
        outputDirectory: Path,
        maxProductsPerShard: Int,
        maxBytesPerShard: Int = DEFAULT_MAX_MANIFEST_BYTES
    ): ImportManifestIndex {
        require(maxProductsPerShard > 0) { "Maximum products per shard must be positive." }
        require(maxBytesPerShard > 0) { "Maximum bytes per shard must be positive." }
        requirePrivateLocation(outputDirectory)
        require(!Files.exists(outputDirectory)) { "Manifest shard output directory already exists." }
        val source = ImportManifestFile.read(sourceFile)
        val prices = validatedPrices(source)
        val encodedShards = createShards(source, prices, maxProductsPerShard, maxBytesPerShard)
        val index = ImportManifestIndex(
            schemaVersion = IMPORT_MANIFEST_INDEX_SCHEMA_VERSION,
            sourceManifestFile = sourceFile.fileName.toString(),
            sourceBatchId = source.batchId,
            sourceSha256 = sha256Hex(Files.readAllBytes(sourceFile)),
            maxProductsPerShard = maxProductsPerShard,
            maxBytesPerShard = maxBytesPerShard,
            totalProductCount = source.products.size,
            totalHistoricalPriceCount = source.historicalPrices.size,
            shards = encodedShards.mapIndexed { index, shard ->
                ImportManifestShard(
                    sequence = index + 1,
                    fileName = shard.fileName,
                    batchId = shard.manifest.batchId,
                    productCount = shard.manifest.products.size,
                    historicalPriceCount = shard.manifest.historicalPrices.size,
                    sha256 = sha256Hex(shard.bytes)
                )
            }
        )
        writeOutput(outputDirectory, encodedShards, index)
        return index
    }

    private fun createShards(
        source: ImportManifest,
        prices: List<KeyedPrice>,
        maxProducts: Int,
        maxBytes: Int
    ): List<EncodedShard> = buildList {
        var current = mutableListOf<ImportProduct>()
        source.products.forEach { product ->
            val candidate = current + product
            val encoded = encodedShard(source, candidate, prices, size + 1)
            if (candidate.size > maxProducts || encoded.bytes.size > maxBytes) {
                require(current.isNotEmpty()) {
                    "Product ${product.productId} exceeds the maximum manifest byte size by itself."
                }
                add(encodedShard(source, current, prices, size + 1))
                current = mutableListOf(product)
                val single = encodedShard(source, current, prices, size + 1)
                require(single.bytes.size <= maxBytes) {
                    "Product ${product.productId} exceeds the maximum manifest byte size by itself."
                }
            } else {
                current.add(product)
            }
        }
        if (current.isNotEmpty()) add(encodedShard(source, current, prices, size + 1))
    }

    private fun validatedPrices(source: ImportManifest): List<KeyedPrice> {
        val products = source.products.associateBy { ProductKey(it.retailer.providerId, it.productId) }
        return source.historicalPrices.map { observation ->
            val key = observation.productKey()
            require(products.containsKey(key)) {
                "Historical observation ${observation.id.value} has no product in the manifest."
            }
            KeyedPrice(key, observation)
        }
    }

    private fun encodedShard(
        source: ImportManifest,
        products: List<ImportProduct>,
        prices: List<KeyedPrice>,
        sequence: Int
    ): EncodedShard {
        val suffix = sequence.toString().padStart(3, '0')
        val keys = products.mapTo(mutableSetOf()) { ProductKey(it.retailer.providerId, it.productId) }
        val manifest = source.copy(
            batchId = "${source.batchId}-part-$suffix",
            products = products,
            historicalPrices = prices.filter { it.key in keys }.map { it.observation }
        )
        return EncodedShard("manifest-part-$suffix.json", manifest, encode(manifest))
    }

    private fun writeOutput(
        directory: Path,
        shards: List<EncodedShard>,
        index: ImportManifestIndex
    ) {
        directory.parent?.let(Files::createDirectories)
        Files.createDirectory(directory)
        setOwnerOnly(directory, "rwx------")
        shards.forEach { shard -> writePrivate(directory.resolve(shard.fileName), shard.bytes) }
        writePrivate(directory.resolve("manifest-index.json"), encode(index))
    }

    private inline fun <reified T> encode(value: T): ByteArray =
        (json.encodeToString(value) + "\n").encodeToByteArray()

    private fun writePrivate(path: Path, bytes: ByteArray) {
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        setOwnerOnly(path, "rw-------")
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

internal fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { byte -> "%02x".format(byte) }

private data class ProductKey(val provider: String, val externalId: String)
private data class KeyedPrice(val key: ProductKey, val observation: HistoricalPriceObservation)
private data class EncodedShard(val fileName: String, val manifest: ImportManifest, val bytes: ByteArray)

private fun HistoricalPriceObservation.productKey(): ProductKey {
    val provider = retailerId.value
    val prefix = "$provider:$region:"
    require(productId.value.startsWith(prefix)) {
        "Historical observation ${id.value} has an incompatible product id."
    }
    return ProductKey(provider, productId.value.removePrefix(prefix))
}

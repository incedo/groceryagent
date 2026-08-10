package com.groceryautomate.importer

import com.groceryautomate.postgres.PostgresSettings
import java.nio.file.Path

data class ImporterSettings(
    val manifestFile: Path,
    val batchIdOverride: String?,
    val picnicEnvironmentFile: Path,
    val providerTimeoutMillis: Long,
    val providerRequestDelayMillis: Long,
    val imageImportLimit: Int,
    val s3Endpoint: String,
    val s3Region: String,
    val s3AccessKey: String?,
    val s3SecretKey: String?,
    val imageBucket: String,
    val assetBaseUrl: String,
    val mode: ImportMode,
    val database: PostgresSettings
) {
    companion object {
        fun fromEnvironment(read: (String) -> String? = System::getenv): ImporterSettings =
            ImporterSettings(
                manifestFile = Path.of(read.value("IMPORT_MANIFEST_FILE", "/app/import-manifest.json")),
                batchIdOverride = read("IMPORT_BATCH_ID")?.trim()?.takeIf(String::isNotEmpty),
                picnicEnvironmentFile = Path.of(read.value("PICNIC_ENV_FILE", "/run/secrets/picnic.env")),
                providerTimeoutMillis = read.positiveLong("PICNIC_TIMEOUT_MILLIS", 15_000),
                providerRequestDelayMillis = read.nonNegativeLong("PICNIC_REQUEST_DELAY_MILLIS", 3_000),
                imageImportLimit = read.positiveInt("IMAGE_IMPORT_LIMIT", 50).also {
                    require(it <= 50) { "IMAGE_IMPORT_LIMIT must not exceed 50." }
                },
                s3Endpoint = read.value("S3_ENDPOINT", "https://minio.home.intelliworks.nl"),
                s3Region = read.value("S3_REGION", "us-east-1"),
                s3AccessKey = read("S3_ACCESS_KEY")?.trim()?.takeIf(String::isNotEmpty),
                s3SecretKey = read("S3_SECRET_KEY")?.trim()?.takeIf(String::isNotEmpty),
                imageBucket = read.value("S3_BUCKET", "grocery-product-images"),
                assetBaseUrl = read.value("ASSET_BASE_URL", "https://assets.home.intelliworks.nl"),
                mode = ImportMode.from(read.value("IMPORT_MODE", "products-and-history")),
                database = PostgresSettings(
                    jdbcUrl = read.value("DATABASE_URL", "jdbc:postgresql://127.0.0.1:5432/grocery"),
                    user = read.value("DATABASE_USER", "grocery"),
                    password = read("DATABASE_PASSWORD") ?: "grocery-local",
                    maximumPoolSize = read.positiveInt("DATABASE_POOL_SIZE", 2),
                    connectionTimeoutMillis = read.positiveLong("DATABASE_TIMEOUT_MILLIS", 5_000)
                )
            )
    }
}

enum class ImportMode {
    PRODUCTS_AND_HISTORY,
    HISTORY_ONLY,
    SEARCH_REPLACEMENTS,
    PRODUCT_IMAGES;

    companion object {
        fun from(value: String): ImportMode = when (value) {
            "products-and-history" -> PRODUCTS_AND_HISTORY
            "history-only" -> HISTORY_ONLY
            "search-replacements" -> SEARCH_REPLACEMENTS
            "product-images" -> PRODUCT_IMAGES
            else -> error(
                "IMPORT_MODE must be products-and-history, history-only, search-replacements, " +
                    "or product-images."
            )
        }
    }
}

private fun ((String) -> String?).value(name: String, default: String): String =
    invoke(name)?.trim()?.takeIf(String::isNotEmpty) ?: default

private fun ((String) -> String?).positiveInt(name: String, default: Int): Int {
    val raw = invoke(name) ?: return default
    return raw.toIntOrNull()?.takeIf { it > 0 } ?: error("$name must be a positive integer.")
}

private fun ((String) -> String?).positiveLong(name: String, default: Long): Long {
    val raw = invoke(name) ?: return default
    return raw.toLongOrNull()?.takeIf { it > 0 } ?: error("$name must be a positive integer.")
}

private fun ((String) -> String?).nonNegativeLong(name: String, default: Long): Long {
    val raw = invoke(name) ?: return default
    return raw.toLongOrNull()?.takeIf { it >= 0 } ?: error("$name must be a non-negative integer.")
}

package com.groceryautomate.importer

import com.groceryautomate.postgres.PostgresSettings
import java.nio.file.Path

data class ImporterSettings(
    val manifestFile: Path,
    val batchIdOverride: String?,
    val picnicEnvironmentFile: Path,
    val providerTimeoutMillis: Long,
    val database: PostgresSettings
) {
    companion object {
        fun fromEnvironment(read: (String) -> String? = System::getenv): ImporterSettings =
            ImporterSettings(
                manifestFile = Path.of(read.value("IMPORT_MANIFEST_FILE", "/app/import-manifest.json")),
                batchIdOverride = read("IMPORT_BATCH_ID")?.trim()?.takeIf(String::isNotEmpty),
                picnicEnvironmentFile = Path.of(read.value("PICNIC_ENV_FILE", "/run/secrets/picnic.env")),
                providerTimeoutMillis = read.positiveLong("PICNIC_TIMEOUT_MILLIS", 15_000),
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

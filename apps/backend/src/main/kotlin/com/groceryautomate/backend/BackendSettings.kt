package com.groceryautomate.backend

import com.groceryautomate.postgres.PostgresSettings

data class BackendSettings(
    val host: String,
    val port: Int,
    val picnicEnvironmentFile: String,
    val providerTimeoutMillis: Long,
    val database: PostgresSettings
) {
    companion object {
        fun fromEnvironment(read: (String) -> String? = System::getenv): BackendSettings =
            BackendSettings(
                host = read("GROCERY_BACKEND_HOST")?.trim()?.takeIf(String::isNotEmpty)
                    ?: "127.0.0.1",
                port = read("GROCERY_BACKEND_PORT").positiveInt("GROCERY_BACKEND_PORT", 8080),
                picnicEnvironmentFile = read("PICNIC_ENV_FILE")?.trim()?.takeIf(String::isNotEmpty)
                    ?: ".env.picnic.local",
                providerTimeoutMillis = read("PICNIC_TIMEOUT_MILLIS")
                    .positiveLong("PICNIC_TIMEOUT_MILLIS", 15_000),
                database = PostgresSettings(
                    jdbcUrl = read("DATABASE_URL")?.trim()?.takeIf(String::isNotEmpty)
                        ?: "jdbc:postgresql://127.0.0.1:5432/grocery",
                    user = read("DATABASE_USER")?.trim()?.takeIf(String::isNotEmpty) ?: "grocery",
                    password = read("DATABASE_PASSWORD") ?: "grocery-local",
                    maximumPoolSize = read("DATABASE_POOL_SIZE")
                        .positiveInt("DATABASE_POOL_SIZE", 8),
                    connectionTimeoutMillis = read("DATABASE_TIMEOUT_MILLIS")
                        .positiveLong("DATABASE_TIMEOUT_MILLIS", 5_000)
                )
            ).also {
                require(it.port in 1..65_535) { "GROCERY_BACKEND_PORT must be between 1 and 65535." }
            }
    }
}

private fun String?.positiveInt(name: String, default: Int): Int = this
    ?.toIntOrNull()
    ?.takeIf { it > 0 }
    ?: if (this == null) default else error("$name must be a positive integer.")

private fun String?.positiveLong(name: String, default: Long): Long = this
    ?.toLongOrNull()
    ?.takeIf { it > 0 }
    ?: if (this == null) default else error("$name must be a positive integer.")

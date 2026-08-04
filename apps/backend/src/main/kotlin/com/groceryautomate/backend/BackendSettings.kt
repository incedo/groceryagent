package com.groceryautomate.backend

data class BackendSettings(
    val host: String,
    val port: Int,
    val picnicEnvironmentFile: String,
    val providerTimeoutMillis: Long
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
                    .positiveLong("PICNIC_TIMEOUT_MILLIS", 15_000)
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

package com.groceryautomate.postgres

data class PostgresSettings(
    val jdbcUrl: String,
    val user: String,
    val password: String,
    val maximumPoolSize: Int = 8,
    val connectionTimeoutMillis: Long = 5_000
) {
    init {
        require(jdbcUrl.startsWith("jdbc:postgresql://")) { "DATABASE_URL must be a PostgreSQL JDBC URL." }
        require(user.isNotBlank()) { "Database user must not be blank." }
        require(maximumPoolSize > 0) { "Database pool size must be positive." }
        require(connectionTimeoutMillis > 0) { "Database timeout must be positive." }
    }
}

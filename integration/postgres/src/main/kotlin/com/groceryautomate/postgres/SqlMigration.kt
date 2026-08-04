package com.groceryautomate.postgres

import java.security.MessageDigest

data class SqlMigration(
    val version: Int,
    val name: String,
    val sql: String
) {
    val checksum: String = MessageDigest.getInstance("SHA-256")
        .digest(sql.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    init {
        require(version > 0) { "Migration version must be positive." }
        require(name.isNotBlank()) { "Migration name must not be blank." }
        require(sql.isNotBlank()) { "Migration SQL must not be blank." }
    }
}

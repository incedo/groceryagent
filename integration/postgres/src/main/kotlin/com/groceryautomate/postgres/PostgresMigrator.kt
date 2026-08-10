package com.groceryautomate.postgres

import java.sql.Connection
import javax.sql.DataSource

class PostgresMigrator(
    private val dataSource: DataSource,
    private val migrations: List<SqlMigration> = loadDefaultMigrations()
) {
    fun migrate(): Int = dataSource.connection.use { connection ->
        connection.inTransaction {
            createMigrationTable()
            prepareStatement("SELECT pg_advisory_xact_lock(?)").use {
                it.setLong(1, MIGRATION_LOCK_ID)
                it.execute()
            }
            val applied = appliedMigrations()
            migrations.sortedBy(SqlMigration::version).count { migration ->
                val previous = applied[migration.version]
                check(previous == null || previous == (migration.name to migration.checksum)) {
                    "Migration V${migration.version} checksum or name changed after application."
                }
                if (previous == null) {
                    createStatement().use { it.execute(migration.sql) }
                    prepareStatement(
                        "INSERT INTO schema_migrations(version, name, checksum) VALUES (?, ?, ?)"
                    ).use {
                        it.setInt(1, migration.version)
                        it.setString(2, migration.name)
                        it.setString(3, migration.checksum)
                        it.executeUpdate()
                    }
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun Connection.createMigrationTable() {
        createStatement().use {
            it.execute(
                """
                CREATE TABLE IF NOT EXISTS schema_migrations (
                    version INTEGER PRIMARY KEY,
                    name TEXT NOT NULL,
                    checksum TEXT NOT NULL,
                    applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
                )
                """.trimIndent()
            )
        }
    }

    private fun Connection.appliedMigrations(): Map<Int, Pair<String, String>> =
        createStatement().use { statement ->
            statement.executeQuery("SELECT version, name, checksum FROM schema_migrations").use { result ->
                buildMap {
                    while (result.next()) put(result.getInt(1), result.getString(2) to result.getString(3))
                }
            }
        }

    private companion object {
        const val MIGRATION_LOCK_ID = 7_425_846_392_001L

        fun loadDefaultMigrations(): List<SqlMigration> {
            return listOf(
                loadMigration(1, "event_store"),
                loadMigration(2, "historical_price_projection"),
                loadMigration(3, "product_previous_ids"),
                loadMigration(4, "product_image_assets")
            )
        }

        private fun loadMigration(version: Int, name: String): SqlMigration {
            val path = "db/migration/V${version.toString().padStart(3, '0')}__${name}.sql"
            val resource = checkNotNull(PostgresMigrator::class.java.classLoader.getResource(path)) {
                "Missing migration $path."
            }
            return SqlMigration(version, name, resource.readText())
        }
    }
}

internal inline fun <T> Connection.inTransaction(block: Connection.() -> T): T {
    val previousAutoCommit = autoCommit
    autoCommit = false
    return try {
        block().also { commit() }
    } catch (failure: Throwable) {
        rollback()
        throw failure
    } finally {
        autoCommit = previousAutoCommit
    }
}

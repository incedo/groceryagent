package com.groceryautomate.postgres

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.postgresql.ds.PGSimpleDataSource
import javax.sql.DataSource

object PostgresDataSource {
    fun create(settings: PostgresSettings): ManagedPostgresDataSource {
        val postgres = PGSimpleDataSource().apply {
            setUrl(settings.jdbcUrl)
            user = settings.user
            password = settings.password
        }
        val pool = HikariConfig().apply {
            dataSource = postgres
            maximumPoolSize = settings.maximumPoolSize
            connectionTimeout = settings.connectionTimeoutMillis
            initializationFailTimeout = -1
            poolName = "grocery-postgres"
            isAutoCommit = true
        }
        return ManagedPostgresDataSource(HikariDataSource(pool))
    }
}

class ManagedPostgresDataSource internal constructor(
    private val pool: HikariDataSource
) : DataSource by pool, AutoCloseable {
    override fun close() = pool.close()
}

package com.groceryautomate.backend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BackendSettingsTest {
    @Test
    fun defaultsToLocalOnlyAndIgnoredEnvironmentFile() {
        val settings = BackendSettings.fromEnvironment { null }

        assertEquals("127.0.0.1", settings.host)
        assertEquals(8080, settings.port)
        assertEquals(".env.picnic.local", settings.picnicEnvironmentFile)
        assertEquals(15_000, settings.providerTimeoutMillis)
        assertEquals("jdbc:postgresql://127.0.0.1:5432/grocery", settings.database.jdbcUrl)
        assertEquals("grocery", settings.database.user)
    }

    @Test
    fun acceptsExplicitRuntimeSettings() {
        val values = mapOf(
            "GROCERY_BACKEND_HOST" to "0.0.0.0",
            "GROCERY_BACKEND_PORT" to "9090",
            "PICNIC_ENV_FILE" to "/private/auth.env",
            "PICNIC_TIMEOUT_MILLIS" to "5000",
            "DATABASE_URL" to "jdbc:postgresql://db:5432/catalog",
            "DATABASE_USER" to "service",
            "DATABASE_PASSWORD" to "test-password",
            "DATABASE_POOL_SIZE" to "12"
        )

        val settings = BackendSettings.fromEnvironment(values::get)

        assertEquals("0.0.0.0", settings.host)
        assertEquals(9090, settings.port)
        assertEquals("/private/auth.env", settings.picnicEnvironmentFile)
        assertEquals(5000, settings.providerTimeoutMillis)
        assertEquals("jdbc:postgresql://db:5432/catalog", settings.database.jdbcUrl)
        assertEquals("service", settings.database.user)
        assertEquals(12, settings.database.maximumPoolSize)
    }

    @Test
    fun rejectsInvalidPortsWithoutReadingSecrets() {
        assertFailsWith<IllegalArgumentException> {
            BackendSettings.fromEnvironment { if (it == "GROCERY_BACKEND_PORT") "70000" else null }
        }
    }
}

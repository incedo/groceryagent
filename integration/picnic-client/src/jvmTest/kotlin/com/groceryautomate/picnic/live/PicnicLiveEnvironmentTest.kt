package com.groceryautomate.picnic.live

import com.groceryautomate.picnic.adapter.out.config.PicnicEnvironmentFile
import com.groceryautomate.picnic.adapter.out.config.parsePicnicEnvironment
import java.nio.file.Files
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PicnicLiveEnvironmentTest {
    @Test
    fun `loads captured auth shape without exposing values`() {
        val file = createTempFile("picnic-live", ".env")
        Files.writeString(
            file,
            """
            PICNIC_AUTH="secret-token"
            PICNIC_AGENT="30100;1.239.3-#15578"
            PICNIC_DID="A1B2C3D4E5F60718"
            PICNIC_UA="okhttp/test"
            PICNIC_HOST="storefront-prod.de.picnicinternational.com"
            """.trimIndent()
        )

        val environment = PicnicEnvironmentFile.load(file)

        assertEquals("secret-token", environment.authToken)
        assertEquals("de", environment.config.country.apiCode)
        assertEquals(30100, environment.config.clientId)
        assertEquals("1.239.3", environment.config.clientVersion)
        assertEquals(15578, environment.config.buildNumber)
        assertEquals("A1B2C3D4E5F60718", environment.config.deviceId)
        assertEquals("okhttp/test", environment.config.userAgent)
        assertEquals("30100;1.239.3-#15578", environment.config.agent)
    }

    @Test
    fun `supports explicit client overrides and exported values`() {
        val file = createTempFile("picnic-live-overrides", ".env")
        Files.writeString(
            file,
            """
            export PICNIC_AUTH='token'
            PICNIC_DEVICE_ID=device
            PICNIC_COUNTRY=fr
            PICNIC_CLIENT_ID=10100
            PICNIC_CLIENT_VERSION=2.0.0
            PICNIC_BUILD_NUMBER=20000
            PICNIC_API_VERSION=16
            """.trimIndent()
        )

        val environment = PicnicEnvironmentFile.load(file)

        assertEquals("fr", environment.config.country.apiCode)
        assertEquals(10100, environment.config.clientId)
        assertEquals("2.0.0", environment.config.clientVersion)
        assertEquals(20000, environment.config.buildNumber)
        assertEquals(16, environment.config.apiVersion)
        assertEquals("10100;2.0.0-#20000", environment.config.agent)
    }

    @Test
    fun `rejects malformed files without echoing secret values`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            parsePicnicEnvironment(listOf("not an assignment with secret-token"))
        }

        assertEquals("Invalid environment assignment on line 1.", failure.message)
    }
}

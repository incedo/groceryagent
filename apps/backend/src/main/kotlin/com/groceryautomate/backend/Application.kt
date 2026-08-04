package com.groceryautomate.backend

import com.groceryautomate.picnic.PicnicClient
import com.groceryautomate.picnic.adapter.`in`.catalog.PicnicCanonicalCatalogAdapter
import com.groceryautomate.picnic.adapter.out.config.PicnicEnvironmentFile
import com.groceryautomate.picnic.adapter.out.http.KtorPicnicHttpTransport
import com.groceryautomate.picnic.adapter.out.memory.InMemoryPicnicAuthStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.HttpTimeout
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.nio.file.Path

fun main() {
    val settings = BackendSettings.fromEnvironment()
    val picnicEnvironment = PicnicEnvironmentFile.load(Path.of(settings.picnicEnvironmentFile))
    val httpClient = HttpClient(Java) {
        install(HttpTimeout) {
            requestTimeoutMillis = settings.providerTimeoutMillis
        }
    }
    try {
        val picnic = PicnicClient(
            config = picnicEnvironment.config,
            transport = KtorPicnicHttpTransport(httpClient),
            authStore = InMemoryPicnicAuthStore(picnicEnvironment.authToken)
        )
        val catalog = PicnicCanonicalCatalogAdapter(picnic.catalog)
        embeddedServer(Netty, host = settings.host, port = settings.port) {
            catalogModule(catalog)
        }.start(wait = true)
    } finally {
        httpClient.close()
    }
}

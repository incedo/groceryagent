package com.groceryautomate.backend

import com.groceryautomate.events.ProductImportService
import com.groceryautomate.picnic.PicnicClient
import com.groceryautomate.picnic.adapter.`in`.catalog.PicnicCanonicalCatalogAdapter
import com.groceryautomate.picnic.adapter.out.config.PicnicEnvironmentFile
import com.groceryautomate.picnic.adapter.out.http.KtorPicnicHttpTransport
import com.groceryautomate.picnic.adapter.out.memory.InMemoryPicnicAuthStore
import com.groceryautomate.postgres.PostgresCatalogEventRepository
import com.groceryautomate.postgres.PostgresDataSource
import com.groceryautomate.postgres.PostgresMigrator
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.HttpTimeout
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

fun main(args: Array<String>) {
    if (args.contentEquals(arrayOf("--healthcheck"))) {
        kotlin.system.exitProcess(if (backendHealthcheck()) 0 else 1)
    }
    val settings = BackendSettings.fromEnvironment()
    val dataSource = PostgresDataSource.create(settings.database)
    val httpClient = HttpClient(Java) {
        install(HttpTimeout) { requestTimeoutMillis = settings.providerTimeoutMillis }
    }
    try {
        PostgresMigrator(dataSource).migrate()
        val repository = PostgresCatalogEventRepository(dataSource)
        val provider = createProvider(settings, httpClient)
        val gateway = ProviderCatalogGateway(provider)
        val imports = ProductImportService(
            provider = gateway,
            repository = repository,
            nextEventId = { UUID.randomUUID().toString() },
            now = { Instant.now().toString() }
        )
        embeddedServer(CIO, host = settings.host, port = settings.port) {
            catalogModule(repository, gateway, imports, repository::isReady)
        }.start(wait = true)
    } finally {
        httpClient.close()
        dataSource.close()
    }
}

private fun createProvider(settings: BackendSettings, httpClient: HttpClient) =
    Path.of(settings.picnicEnvironmentFile).takeIf(Files::isRegularFile)?.let { path ->
        val environment = PicnicEnvironmentFile.load(path)
        val picnic = PicnicClient(
            config = environment.config,
            transport = KtorPicnicHttpTransport(httpClient),
            authStore = InMemoryPicnicAuthStore(environment.authToken)
        )
        PicnicCanonicalCatalogAdapter(picnic.catalog)
    }

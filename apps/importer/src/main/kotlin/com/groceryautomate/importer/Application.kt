package com.groceryautomate.importer

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
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

fun main(args: Array<String>) {
    if (args.firstOrNull() == "--validate-manifest") {
        require(args.size == 2) { "Usage: --validate-manifest <path>" }
        val manifest = ImportManifestFile.read(Path.of(args[1]))
        println("Valid import manifest ${manifest.batchId}: ${manifest.products.size} products.")
        return
    }
    require(args.isEmpty()) { "The importer accepts no arguments; configure it through environment variables." }
    val settings = ImporterSettings.fromEnvironment()
    val fileManifest = ImportManifestFile.read(settings.manifestFile)
    val manifest = settings.batchIdOverride?.let { fileManifest.copy(batchId = it) } ?: fileManifest
    val report = runImport(settings, manifest)
    report.results.forEach { println("${it.productId}: ${it.status} (${it.eventCount} events)") }
    println("Import batch ${report.batchId}: ${report.results.size} products, ${report.failureCount} failures.")
    if (!report.successful) kotlin.system.exitProcess(1)
}

private fun runImport(settings: ImporterSettings, manifest: ImportManifest): ImportReport {
    val environment = PicnicEnvironmentFile.load(settings.picnicEnvironmentFile)
    val httpClient = HttpClient(Java) {
        install(HttpTimeout) { requestTimeoutMillis = settings.providerTimeoutMillis }
    }
    return try {
        val dataSource = PostgresDataSource.create(settings.database)
        try {
            PostgresMigrator(dataSource).migrate()
            val repository = PostgresCatalogEventRepository(dataSource)
            val picnic = PicnicClient(
                config = environment.config,
                transport = KtorPicnicHttpTransport(httpClient),
                authStore = InMemoryPicnicAuthStore(environment.authToken)
            )
            val imports = ProductImportService(
                PicnicCanonicalCatalogAdapter(picnic.catalog),
                repository,
                { UUID.randomUUID().toString() },
                { Instant.now().toString() }
            )
            runBlocking { BatchProductImporter(imports).run(manifest) }
        } finally {
            dataSource.close()
        }
    } finally {
        httpClient.close()
    }
}

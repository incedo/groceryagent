package com.groceryautomate.importer

import com.groceryautomate.events.ProductImportService
import com.groceryautomate.events.HistoricalPriceImportService
import com.groceryautomate.events.ProductReplacementImportService
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

fun main(args: Array<String>) {
    if (args.firstOrNull() == "--capture-orders") {
        require(args.size == 2) { "Usage: --capture-orders <new-private-directory>" }
        val result = runBlocking { captureOrders(Path.of(args[1])) }
        println("Captured ${result.deliveryCount} completed deliveries in ${result.directory}.")
        println("Private local data: review it and delete it after manifest generation.")
        return
    }
    if (args.firstOrNull() == "--orders-to-manifest") {
        require(args.size == 4) {
            "Usage: --orders-to-manifest <capture-directory> <new-manifest-file> <batch-id>"
        }
        val manifest = OrderCaptureFiles().toManifest(Path.of(args[1]), Path.of(args[2]), args[3])
        println("Generated import manifest ${args[2]} with ${manifest.products.size} products and " +
            "${manifest.historicalPrices.size} historical prices.")
        return
    }
    if (args.firstOrNull() == "--validate-manifest") {
        require(args.size == 2) { "Usage: --validate-manifest <path>" }
        val manifest = ImportManifestFile.read(Path.of(args[1]))
        println("Valid import manifest ${manifest.batchId}: ${manifest.products.size} products and " +
            "${manifest.historicalPrices.size} historical prices.")
        return
    }
    if (args.firstOrNull() == "--split-manifest") {
        require(args.size in 4..5) {
            "Usage: --split-manifest <manifest-file> <new-output-directory> <max-products> [max-bytes]"
        }
        val maxProducts = args[3].toIntOrNull()
            ?: error("Maximum products per shard must be an integer.")
        val maxBytes = args.getOrNull(4)?.let { value ->
            value.toIntOrNull() ?: error("Maximum bytes per shard must be an integer.")
        } ?: DEFAULT_MAX_MANIFEST_BYTES
        val index = ImportManifestSplitter().split(
            Path.of(args[1]), Path.of(args[2]), maxProducts, maxBytes
        )
        println("Split ${index.totalProductCount} products and ${index.totalHistoricalPriceCount} " +
            "historical prices into ${index.shards.size} manifests in ${args[2]}.")
        return
    }
    require(args.isEmpty()) { "The importer accepts no arguments; configure it through environment variables." }
    val settings = ImporterSettings.fromEnvironment()
    if (settings.mode == ImportMode.PRODUCT_IMAGES) {
        printProductImageReport(runProductImageImport(settings))
        return
    }
    val fileManifest = ImportManifestFile.read(settings.manifestFile)
    val manifest = settings.batchIdOverride?.let { fileManifest.copy(batchId = it) } ?: fileManifest
    when (settings.mode) {
        ImportMode.PRODUCTS_AND_HISTORY -> printImportReport(runImport(settings, manifest))
        ImportMode.HISTORY_ONLY -> printImportReport(runHistoricalPriceImport(settings, manifest))
        ImportMode.SEARCH_REPLACEMENTS -> printReplacementReport(
            runProductReplacementImport(settings, manifest)
        )
        ImportMode.PRODUCT_IMAGES -> error("Product image mode does not use a manifest.")
    }
}

private fun printImportReport(report: ImportReport) {
    report.results.forEach { println(it.toLogLine()) }
    println("Import batch ${report.batchId}: ${report.results.size} products, " +
        "${report.historicalPriceResults.size} historical prices, ${report.failureCount} failures.")
    if (!report.successful) kotlin.system.exitProcess(1)
}

private fun printReplacementReport(report: ProductReplacementImportReport) {
    report.results.forEach { println(it.toLogLine()) }
    println("Replacement import batch ${report.batchId}: ${report.results.size} products, " +
        "${report.failureCount} failures.")
    if (!report.successful) kotlin.system.exitProcess(1)
}

private suspend fun captureOrders(directory: Path): OrderCaptureResult {
    val envFile = System.getenv("PICNIC_ENV_FILE")
        ?.trim()?.takeIf(String::isNotEmpty)
        ?: error("PICNIC_ENV_FILE must explicitly select the other account environment file.")
    val environment = PicnicEnvironmentFile.load(Path.of(envFile))
    val timeout = System.getenv("PICNIC_TIMEOUT_MILLIS")
        ?.toLongOrNull()?.takeIf { it > 0 } ?: 15_000
    return HttpClient(Java) {
        install(HttpTimeout) { requestTimeoutMillis = timeout }
    }.use { httpClient ->
        val picnic = PicnicClient(
            config = environment.config,
            transport = KtorPicnicHttpTransport(httpClient),
            authStore = InMemoryPicnicAuthStore(environment.authToken)
        )
        OrderCaptureService(picnic.delivery).captureCompletedOrders(directory)
    }
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
            val historicalPrices = HistoricalPriceImportService(repository) { UUID.randomUUID().toString() }
            runBlocking {
                BatchProductImporter(
                    imports,
                    BatchHistoricalPriceImporter(historicalPrices),
                    { delay(settings.providerRequestDelayMillis) },
                    ::classifyPicnicImportFailure
                ).run(manifest)
            }
        } finally {
            dataSource.close()
        }
    } finally {
        httpClient.close()
    }
}

private fun runHistoricalPriceImport(settings: ImporterSettings, manifest: ImportManifest): ImportReport {
    require(manifest.historicalPrices.isNotEmpty()) { "History-only import requires historical prices." }
    val dataSource = PostgresDataSource.create(settings.database)
    return try {
        PostgresMigrator(dataSource).migrate()
        val repository = PostgresCatalogEventRepository(dataSource)
        val service = HistoricalPriceImportService(repository) { UUID.randomUUID().toString() }
        val results = runBlocking { BatchHistoricalPriceImporter(service).run(manifest) }
        ImportReport(manifest.batchId, emptyList(), results)
    } finally {
        dataSource.close()
    }
}

private fun runProductReplacementImport(
    settings: ImporterSettings,
    manifest: ImportManifest
): ProductReplacementImportReport {
    manifest.products.forEach(ImportProduct::requireHistoricalReference)
    require(manifest.historicalPrices.isEmpty()) {
        "Search replacement mode does not import historical prices; run them in history-only mode."
    }
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
            val service = ProductReplacementImportService(
                PicnicCanonicalCatalogAdapter(picnic.catalog),
                repository,
                { UUID.randomUUID().toString() },
                { Instant.now().toString() }
            )
            runBlocking {
                BatchProductReplacementImporter(
                    service,
                    { delay(settings.providerRequestDelayMillis) },
                    ::classifyPicnicImportFailure
                ).run(manifest)
            }
        } finally {
            dataSource.close()
        }
    } finally {
        httpClient.close()
    }
}

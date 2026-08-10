package com.groceryautomate.importer

import com.groceryautomate.events.ProductImageImportService
import com.groceryautomate.picnic.PicnicClient
import com.groceryautomate.picnic.adapter.`in`.catalog.PicnicProductImageSource
import com.groceryautomate.picnic.adapter.out.config.PicnicEnvironmentFile
import com.groceryautomate.picnic.adapter.out.http.KtorPicnicHttpTransport
import com.groceryautomate.picnic.adapter.out.memory.InMemoryPicnicAuthStore
import com.groceryautomate.postgres.PostgresCatalogEventRepository
import com.groceryautomate.postgres.PostgresDataSource
import com.groceryautomate.postgres.PostgresMigrator
import com.groceryautomate.storage.MinioProductImageObjectStore
import com.groceryautomate.storage.ProductImageStorageSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.util.UUID

internal fun runProductImageImport(settings: ImporterSettings): ProductImageImportReport {
    val environment = PicnicEnvironmentFile.load(settings.picnicEnvironmentFile)
    val storageSettings = ProductImageStorageSettings(
        endpoint = settings.s3Endpoint,
        region = settings.s3Region,
        accessKey = requireNotNull(settings.s3AccessKey) { "S3_ACCESS_KEY is required." },
        secretKey = requireNotNull(settings.s3SecretKey) { "S3_SECRET_KEY is required." },
        bucket = settings.imageBucket,
        publicBaseUrl = settings.assetBaseUrl
    )
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
            runBlocking {
                BatchProductImageImporter(
                    source = PicnicProductImageSource(picnic.catalog),
                    objectStore = MinioProductImageObjectStore(storageSettings),
                    assets = repository,
                    events = ProductImageImportService(repository) { UUID.randomUUID().toString() },
                    now = { Instant.now().toString() },
                    awaitNextImage = { delay(settings.providerRequestDelayMillis) },
                    classifyFailure = ::classifyPicnicImportFailure
                ).run(settings.imageImportLimit)
            }
        } finally {
            dataSource.close()
        }
    } finally {
        httpClient.close()
    }
}

internal fun printProductImageReport(report: ProductImageImportReport) {
    report.results.forEach { result ->
        println(buildString {
            append("product=").append(result.productId)
            append(" source_image=").append(result.sourceImageId)
            append(" status=").append(result.status)
            append(" events=").append(result.eventCount)
            result.failure?.let(::appendDiagnostic)
        })
    }
    println("Product image import: ${report.results.size} candidates, ${report.failureCount} failures.")
    if (!report.successful) kotlin.system.exitProcess(1)
}

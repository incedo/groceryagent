package com.groceryautomate.picnic.live

import com.groceryautomate.picnic.PicnicClient
import com.groceryautomate.picnic.adapter.out.config.PicnicEnvironmentFile
import com.groceryautomate.picnic.adapter.out.http.KtorPicnicHttpTransport
import com.groceryautomate.picnic.adapter.out.memory.InMemoryPicnicAuthStore
import com.groceryautomate.picnic.domain.PicnicApiException
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

private const val DEFAULT_RESULT_LIMIT = 10

fun main() = runBlocking {
    val envPath = Path.of(requiredProperty("picnic.env.file"))
    val query = requiredProperty("picnic.query")
    val requestedProductId = System.getProperty("picnic.product.id")?.trim()?.takeIf(String::isNotEmpty)
    val environment = PicnicEnvironmentFile.load(envPath)

    HttpClient(Java).use { httpClient ->
        val picnic = PicnicClient(
            config = environment.config,
            transport = KtorPicnicHttpTransport(httpClient),
            authStore = InMemoryPicnicAuthStore(environment.authToken)
        )
        try {
            val search = picnic.catalog.search(query)
            println("Picnic live search succeeded: ${search.products.size} products for '$query'.")
            search.products.take(DEFAULT_RESULT_LIMIT).forEach { product ->
                val price = product.priceCents?.let { "${it} cents" } ?: "price unknown"
                println("- ${product.id} | ${product.name} | $price | ${product.unitQuantity.orEmpty()}")
            }

            val productId = requestedProductId ?: search.products.firstOrNull()?.id
                ?: error("Search returned no product to use for the detail smoke test.")
            val details = picnic.catalog.getProductDetails(productId)
            println("Picnic live product detail succeeded:")
            println("- ${details.product.id} | ${details.product.name}")
            println("- route=${details.source.routeGeneration}, endpoint=${details.source.endpoint}")
            println("- ingredients=${if (details.ingredients == null) "unknown" else "available"}")
            println("- allergens=${details.allergens.status}, nutrition=${details.nutrition?.basis ?: "unknown"}")
        } catch (failure: PicnicApiException) {
            if (failure.statusCode == 401) {
                error("Picnic rejected the session. Regenerate auth.env from a fresh authenticated capture.")
            }
            throw failure
        }
    }
}

private fun requiredProperty(name: String): String = System.getProperty(name)
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?: error("Missing required system property: $name")

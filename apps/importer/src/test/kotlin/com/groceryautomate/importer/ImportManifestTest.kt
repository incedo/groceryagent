package com.groceryautomate.importer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class ImportManifestTest {
    @Test
    fun readsValidManifestAndBuildsStablePerBatchCommandIds() {
        val first = ImportProduct(ImportRetailer.PICNIC, "s1001")
        val second = ImportProduct(ImportRetailer.PICNIC, "s1002")
        val manifest = ImportManifest(1, "seed-2026-08-09", "catalog-importer", listOf(first, second))

        assertEquals(importCommandId(manifest.batchId, first), importCommandId(manifest.batchId, first))
        assertNotEquals(importCommandId(manifest.batchId, first), importCommandId(manifest.batchId, second))
        assertNotEquals(importCommandId(manifest.batchId, first), importCommandId("next-batch", first))
    }

    @Test
    fun rejectsUnsupportedEmptyAndDuplicateManifests() {
        val product = ImportProduct(ImportRetailer.PICNIC, "s1001")

        assertFailsWith<IllegalArgumentException> {
            ImportManifest(2, "batch", "importer", listOf(product))
        }
        assertFailsWith<IllegalArgumentException> {
            ImportManifest(1, "batch", "importer", emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            ImportManifest(1, "batch", "importer", listOf(product, product))
        }
    }

    @Test
    fun deploymentCanOverrideBatchIdWithoutChangingTheManifest() {
        val settings = ImporterSettings.fromEnvironment { name ->
            if (name == "IMPORT_BATCH_ID") " catalog-importer-12345 " else null
        }

        assertEquals("catalog-importer-12345", settings.batchIdOverride)
    }

    @Test
    fun historyOnlyModeIsExplicitAndInvalidModesFailClosed() {
        val settings = ImporterSettings.fromEnvironment { name ->
            if (name == "IMPORT_MODE") "history-only" else null
        }

        assertEquals(ImportMode.HISTORY_ONLY, settings.mode)
        assertFailsWith<IllegalStateException> {
            ImporterSettings.fromEnvironment { name -> if (name == "IMPORT_MODE") "fast" else null }
        }
    }

    @Test
    fun searchReplacementModeAndHistoricalReferenceAreExplicit() {
        val settings = ImporterSettings.fromEnvironment { name ->
            if (name == "IMPORT_MODE") "search-replacements" else null
        }
        val product = ImportProduct(
            ImportRetailer.PICNIC,
            "s1001",
            historicalName = "Chicken tenderloins",
            historicalUnitQuantity = "300 gram",
            historicalImageId = "image-1"
        )

        assertEquals(ImportMode.SEARCH_REPLACEMENTS, settings.mode)
        product.requireHistoricalReference()
        assertFailsWith<IllegalArgumentException> {
            ImportProduct(ImportRetailer.PICNIC, "s1002", historicalName = "Incomplete")
        }
    }

    @Test
    fun productRequestDelayHasASafeDefaultAndAcceptsExplicitZero() {
        assertEquals(3_000, ImporterSettings.fromEnvironment { null }.providerRequestDelayMillis)
        assertEquals(
            0,
            ImporterSettings.fromEnvironment { name ->
                if (name == "PICNIC_REQUEST_DELAY_MILLIS") "0" else null
            }.providerRequestDelayMillis
        )
        assertFailsWith<IllegalStateException> {
            ImporterSettings.fromEnvironment { name ->
                if (name == "PICNIC_REQUEST_DELAY_MILLIS") "-1" else null
            }
        }
    }

    @Test
    fun productImageModeIsBoundedAndUsesHomelabStorageDefaults() {
        val settings = ImporterSettings.fromEnvironment { name ->
            when (name) {
                "IMPORT_MODE" -> "product-images"
                "IMAGE_IMPORT_LIMIT" -> "25"
                else -> null
            }
        }

        assertEquals(ImportMode.PRODUCT_IMAGES, settings.mode)
        assertEquals(25, settings.imageImportLimit)
        assertEquals("https://minio.home.intelliworks.nl", settings.s3Endpoint)
        assertEquals("us-east-1", settings.s3Region)
        assertEquals("grocery-product-images", settings.imageBucket)
        assertFailsWith<IllegalArgumentException> {
            ImporterSettings.fromEnvironment { name ->
                if (name == "IMAGE_IMPORT_LIMIT") "51" else null
            }
        }
    }
}

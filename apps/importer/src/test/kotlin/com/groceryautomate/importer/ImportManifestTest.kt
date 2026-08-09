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
}

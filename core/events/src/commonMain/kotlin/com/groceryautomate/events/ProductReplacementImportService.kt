package com.groceryautomate.events

import com.groceryautomate.catalog.HistoricalProductReference
import com.groceryautomate.catalog.ProductCatalogPort
import com.groceryautomate.catalog.ProductId
import com.groceryautomate.catalog.ProductReplacementMatch
import com.groceryautomate.catalog.ProductReplacementMatcher
import com.groceryautomate.catalog.ProviderEvidence

class ProductReplacementImportService(
    private val provider: ProductCatalogPort,
    private val repository: CatalogEventRepository,
    private val nextEventId: () -> String,
    private val now: () -> String
) {
    suspend fun importReplacement(
        reference: HistoricalProductReference,
        commandId: CommandId,
        producerId: ProducerId
    ): ProductReplacementImportResult {
        repository.findCommand(commandId)?.let { duplicate ->
            return ProductReplacementImportResult(
                reference.previousProductId,
                duplicate.streamId.toProductId(),
                ProductReplacementImportStatus.ALREADY_IMPORTED,
                duplicate.eventCount
            )
        }
        val search = provider.search(reference.name, MAX_SEARCH_RESULTS)
        return when (val match = ProductReplacementMatcher.match(reference, search)) {
            ProductReplacementMatch.NoMatch -> result(reference, ProductReplacementImportStatus.NO_MATCH)
            ProductReplacementMatch.SameId -> result(reference, ProductReplacementImportStatus.SAME_ID)
            is ProductReplacementMatch.Ambiguous -> ProductReplacementImportResult(
                reference.previousProductId,
                null,
                ProductReplacementImportStatus.AMBIGUOUS,
                candidateIds = match.productIds
            )
            is ProductReplacementMatch.Matched -> importMatch(
                reference,
                match.product.product.id,
                match.product.evidence,
                commandId,
                producerId
            )
        }
    }

    private suspend fun importMatch(
        reference: HistoricalProductReference,
        currentId: ProductId,
        searchEvidence: ProviderEvidence,
        commandId: CommandId,
        producerId: ProducerId
    ): ProductReplacementImportResult {
        val existing = repository.getProduct(currentId)
        if (existing?.product?.previousIds?.contains(reference.previousProductId) == true) {
            return ProductReplacementImportResult(
                reference.previousProductId,
                currentId,
                ProductReplacementImportStatus.ALREADY_LINKED
            )
        }
        val occurredAt = now()
        val events = if (existing == null) {
            val details = provider.getProduct(currentId)
                ?: return result(reference, ProductReplacementImportStatus.DETAIL_NOT_FOUND, currentId)
            require(details.product.id == currentId) { "Replacement detail changed product identity." }
            buildList {
                add(proposed(occurredAt, ProductImported(details.product, details.composition, details.evidence)))
                details.offers.forEach { add(proposed(occurredAt, OfferObserved(it))) }
                add(proposed(occurredAt, reference.linkEvent(currentId, searchEvidence)))
            }
        } else {
            listOf(proposed(occurredAt, reference.linkEvent(currentId, searchEvidence)))
        }
        val append = append(commandId, currentId, producerId, events)
        return ProductReplacementImportResult(
            reference.previousProductId,
            currentId,
            if (existing == null) ProductReplacementImportStatus.IMPORTED else
                ProductReplacementImportStatus.LINKED_EXISTING,
            append.eventCount
        )
    }

    private suspend fun append(
        commandId: CommandId,
        currentId: ProductId,
        producerId: ProducerId,
        events: List<ProposedCatalogEvent>
    ): AppendResult {
        val streamId = StreamId("product:${currentId.value}")
        repeat(MAX_APPEND_ATTEMPTS) { attempt ->
            try {
                return repository.append(
                    AppendCatalogEvents(
                        commandId,
                        streamId,
                        repository.streamVersion(streamId),
                        producerId,
                        commandId,
                        events = events
                    )
                )
            } catch (conflict: StreamVersionConflict) {
                if (attempt == MAX_APPEND_ATTEMPTS - 1) throw conflict
            }
        }
        error("Product replacement retry loop terminated unexpectedly.")
    }

    private fun proposed(occurredAt: String, event: CatalogEvent) = ProposedCatalogEvent(
        EventId(nextEventId()),
        occurredAt,
        event
    )

    private fun HistoricalProductReference.linkEvent(
        currentId: ProductId,
        evidence: ProviderEvidence
    ) = PreviousProductIdLinked(currentId, previousProductId, name, unitQuantity, evidence)

    private fun result(
        reference: HistoricalProductReference,
        status: ProductReplacementImportStatus,
        currentId: ProductId? = null
    ) = ProductReplacementImportResult(reference.previousProductId, currentId, status)

    private fun StreamId.toProductId() = ProductId(value.removePrefix("product:"))

    private companion object {
        const val MAX_SEARCH_RESULTS = 100
        const val MAX_APPEND_ATTEMPTS = 3
    }
}

data class ProductReplacementImportResult(
    val previousProductId: ProductId,
    val currentProductId: ProductId?,
    val status: ProductReplacementImportStatus,
    val eventCount: Int = 0,
    val candidateIds: List<ProductId> = emptyList()
)

enum class ProductReplacementImportStatus {
    IMPORTED,
    LINKED_EXISTING,
    ALREADY_IMPORTED,
    ALREADY_LINKED,
    NO_MATCH,
    AMBIGUOUS,
    SAME_ID,
    DETAIL_NOT_FOUND,
    FAILED
}

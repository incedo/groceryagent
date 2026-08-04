package com.groceryautomate.events

import com.groceryautomate.catalog.CatalogProduct

fun reduceCatalogProduct(current: CatalogProduct?, event: CatalogEvent): CatalogProduct = when (event) {
    is ProductImported -> {
        current?.let {
            require(it.product.id == event.product.id) { "Product import changed stream identity." }
        }
        CatalogProduct(
            product = event.product,
            composition = event.composition,
            offers = current?.offers.orEmpty(),
            evidence = event.evidence
        )
    }

    is OfferObserved -> {
        val product = requireNotNull(current) { "Offer observation requires an imported product." }
        require(event.offer.productId == product.product.id) { "Offer belongs to another product." }
        product.copy(
            offers = product.offers.filterNot { it.id == event.offer.id } + event.offer
        )
    }
}

package com.groceryautomate.picnic.adapter.`in`.catalog

import com.groceryautomate.catalog.ProductImageSource
import com.groceryautomate.catalog.ProductImageVariant
import com.groceryautomate.picnic.application.port.`in`.PicnicCatalogPort
import com.groceryautomate.picnic.domain.PicnicImageSize

class PicnicProductImageSource(
    private val catalog: PicnicCatalogPort
) : ProductImageSource {
    override suspend fun getPng(
        sourceImageId: String,
        variant: ProductImageVariant
    ): ByteArray = catalog.getImage(
        sourceImageId,
        when (variant) {
            ProductImageVariant.LARGE -> PicnicImageSize.LARGE
        }
    )
}

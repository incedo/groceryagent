package com.groceryautomate.picnic.application.service

import com.groceryautomate.picnic.adapter.out.http.PicnicRequester
import com.groceryautomate.picnic.adapter.out.http.encodePath
import com.groceryautomate.picnic.application.port.`in`.PicnicPaymentPort
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class PaymentService(
    private val requester: PicnicRequester
) : PicnicPaymentPort {
    override suspend fun getPaymentProfile(): JsonElement =
        requester.json("GET", "/payment-profile")

    override suspend fun getWalletTransactions(pageNumber: Int): JsonElement {
        require(pageNumber >= 1) { "Wallet page number must be at least 1." }
        return requester.json(
            "POST",
            "/wallet/transactions",
            buildJsonObject { put("page_number", pageNumber) }
        )
    }

    override suspend fun getWalletTransactionDetails(walletTransactionId: String): JsonElement {
        require(walletTransactionId.isNotBlank()) { "Wallet transaction id must not be blank." }
        return requester.json("GET", "/wallet/transactions/${encodePath(walletTransactionId)}")
    }
}

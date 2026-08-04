package com.groceryautomate.picnic.application.port.`in`

import com.groceryautomate.picnic.domain.PicnicImageSize
import com.groceryautomate.picnic.domain.PicnicLoginResult
import com.groceryautomate.picnic.domain.PicnicProductDetails
import com.groceryautomate.picnic.domain.PicnicSearchRequest
import com.groceryautomate.picnic.domain.PicnicSearchResult
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

interface PicnicAppPort {
    suspend fun getBootstrapData(): JsonElement
    suspend fun getPage(pageId: String): JsonElement
    suspend fun resolveDeeplink(url: String): JsonElement
}

interface PicnicAuthPort {
    val authenticated: Boolean
    fun currentAuthKey(): String?
    suspend fun login(username: String, password: String): PicnicLoginResult
    suspend fun generateSecondFactorCode(channel: String = "SMS")
    suspend fun verifySecondFactorCode(code: String)
    suspend fun logout()
    suspend fun generatePhoneVerificationCode(phoneNumber: String)
    suspend fun verifyPhoneNumber(phoneNumber: String, code: String)
}

interface PicnicCartPort {
    suspend fun getCart(): JsonElement
    suspend fun addProductToCart(productId: String, count: Int = 1, contexts: List<JsonObject>? = null): JsonElement
    suspend fun addProductsToCart(products: Map<String, Int>): JsonElement
    suspend fun removeProductFromCart(productId: String, count: Int = 1, contexts: List<JsonObject>? = null): JsonElement
    suspend fun clearCart(): JsonElement
    suspend fun getDeliverySlots(): JsonElement
    suspend fun setDeliverySlot(slotId: String): JsonElement
    suspend fun getOrderStatus(orderId: String): JsonElement
    suspend fun removeGroupFromCart(groupId: String): JsonElement
    suspend fun getMinimumOrderValue(): JsonElement
    suspend fun confirmOrder(orderId: String): JsonElement
}

interface PicnicCatalogPort {
    suspend fun search(query: String): PicnicSearchResult
    suspend fun search(request: PicnicSearchRequest): PicnicSearchResult
    suspend fun getSuggestions(query: String): JsonElement
    suspend fun getProductDetailsPage(productId: String): JsonElement
    suspend fun getProductDetails(productId: String): PicnicProductDetails
    suspend fun getImage(imageId: String, size: PicnicImageSize): ByteArray
    suspend fun getImageAsDataUri(imageId: String, size: PicnicImageSize): String
}

interface PicnicConsentPort {
    suspend fun getConsentSettings(general: Boolean = false): JsonElement
    suspend fun setConsentSettings(input: JsonObject): JsonElement
    suspend fun getConsents(topics: List<String>, strategy: String): JsonElement
    suspend fun getGeneralConsents(): JsonElement
    suspend fun setGeneralConsents(declarations: JsonObject)
}

interface PicnicContentPort {
    suspend fun getFaqContent(): JsonElement
    suspend fun getSearchEmptyState(): JsonElement
}

interface PicnicCustomerServicePort {
    suspend fun getContactInfo(): JsonElement
    suspend fun getMessages(displayPositions: List<String>? = null): JsonElement
    suspend fun getReminders(): JsonElement
    suspend fun setReminders(reminders: JsonElement)
    suspend fun getParcels(): JsonElement
    suspend fun getUnauthenticatedContactInfo(countryCode: String): JsonElement
}

interface PicnicDeliveryPort {
    suspend fun getDeliveries(filter: List<String> = emptyList()): JsonElement
    suspend fun getDelivery(deliveryId: String): JsonElement
    suspend fun getDeliveryPosition(deliveryId: String): JsonElement
    suspend fun getDeliveryScenario(deliveryId: String): JsonElement
    suspend fun cancelDelivery(deliveryId: String): JsonElement
    suspend fun setDeliveryRating(deliveryId: String, rating: Int): JsonElement
    suspend fun sendDeliveryInvoiceEmail(deliveryId: String): JsonElement
}

interface PicnicPaymentPort {
    suspend fun getPaymentProfile(): JsonElement
    suspend fun getWalletTransactions(pageNumber: Int): JsonElement
    suspend fun getWalletTransactionDetails(walletTransactionId: String): JsonElement
}

interface PicnicRecipePort {
    suspend fun getRecipesPage(): JsonElement
    suspend fun getCookbookPage(): JsonElement
    suspend fun getRecipeDetailsPage(recipeId: String): JsonElement
    suspend fun saveRecipe(recipeId: String): JsonElement
    suspend fun unsaveRecipe(recipeId: String): JsonElement
    suspend fun assignSellingGroupToBasket(sellingGroupId: String, dayOffset: Int? = null, portions: Int? = null): JsonElement
    suspend fun updateSellingGroupPortions(sellingGroupId: String, dayOffset: Int, portions: Int): JsonElement
    suspend fun removeSellingGroupFromBasket(sellingGroupId: String): JsonElement
}

interface PicnicUserPort {
    suspend fun getUserDetails(): JsonElement
    suspend fun getUserInfo(): JsonElement
    suspend fun getProfileMenu(): JsonElement
    suspend fun submitSuggestion(suggestion: String): JsonElement
    suspend fun registerPushToken(pushDestination: String, pushVersion: Int = 1): JsonElement
    suspend fun checkForUpdates(): JsonElement
}

interface PicnicUserOnboardingPort {
    suspend fun setHouseholdDetails(details: JsonObject): JsonElement
    suspend fun setBusinessDetails(details: JsonObject): JsonElement
    suspend fun subscribePush(topics: List<String>): JsonElement
}

interface PicnicRawPort {
    suspend fun sendRequest(
        method: String,
        path: String,
        data: JsonElement? = null,
        includePicnicHeaders: Boolean = false
    ): JsonElement
}

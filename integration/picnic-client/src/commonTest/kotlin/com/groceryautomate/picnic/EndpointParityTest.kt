package com.groceryautomate.picnic

import com.groceryautomate.picnic.adapter.out.memory.InMemoryPicnicAuthStore
import com.groceryautomate.picnic.application.port.out.PicnicClock
import com.groceryautomate.picnic.application.port.out.PicnicIdGenerator
import com.groceryautomate.picnic.domain.PicnicClientConfig
import com.groceryautomate.picnic.domain.PicnicImageSize
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EndpointParityTest {
    @Test
    fun exposesEveryUpstreamDomainRoute() = runTest {
        val transport = RecordingTransport { request ->
            if (request.url.contains("/pages/product-details-page-root")) {
                jsonResponse("""{"id":"s1","name":"Fixture product","price":100}""")
            } else {
                jsonResponse("{}")
            }
        }
        val client = PicnicClient(
            PicnicClientConfig(
                baseUrlOverride = "https://picnic.example.test/api/15",
                deviceId = "device-test",
                agent = "30100;1.2.3-4;"
            ),
            transport,
            InMemoryPicnicAuthStore("token"),
            PicnicClock { "2026-08-01T10:15:30Z" },
            idGenerator = PicnicIdGenerator { "11111111-1111-4111-8111-111111111111" }
        )

        invokeApp(client)
        invokeCart(client)
        invokeCatalog(client)
        invokeConsentAndContent(client)
        invokeCustomerService(client)
        invokeDeliveryAndPayment(client)
        invokeRecipes(client)
        invokeUser(client)
        client.raw.sendRequest("DELETE", "/custom/route", buildJsonObject { put("id", "x") }, true)

        assertEquals(65, transport.requests.size)
        expectedRoutes.forEach { (method, suffix) ->
            assertNotNull(
                transport.requests.firstOrNull { it.method == method && it.url.endsWith(suffix) },
                "Missing $method $suffix"
            )
        }
        val cart = transport.requests.first { it.url.endsWith("/cart") }
        assertEquals("token", cart.headers["x-picnic-auth"])
        assertEquals("device-test", cart.headers["x-picnic-did"])
    }
}

private suspend fun invokeApp(client: PicnicClient) {
    client.app.getBootstrapData()
    client.app.getPage("home_page_root")
    client.app.resolveDeeplink("https://picnic.app/nl/deeplink/test")
}

private suspend fun invokeCart(client: PicnicClient) {
    client.cart.getCart()
    client.cart.addProductToCart("s1", 2)
    client.cart.addProductsToCart(mapOf("s1" to 2, "s2" to 1))
    client.cart.removeProductFromCart("s1")
    client.cart.clearCart()
    client.cart.getDeliverySlots()
    client.cart.setDeliverySlot("slot-1")
    client.cart.getOrderStatus("order-1")
    client.cart.removeGroupFromCart("group-1")
    client.cart.getMinimumOrderValue()
    client.cart.confirmOrder("order-1")
}

private suspend fun invokeCatalog(client: PicnicClient) {
    client.catalog.search("milk")
    client.catalog.getSuggestions("milk")
    client.catalog.getProductDetailsPage("s1")
    client.catalog.getProductDetails("s1")
    client.catalog.getImage("image-1", PicnicImageSize.SMALL)
    client.catalog.getImageAsDataUri("image-1", PicnicImageSize.LARGE)
}

private suspend fun invokeConsentAndContent(client: PicnicClient) {
    client.consent.getConsentSettings()
    client.consent.getConsentSettings(general = true)
    client.consent.setConsentSettings(buildJsonObject { put("consent_declarations", buildJsonArray { }) })
    client.consent.getConsents(listOf("MISC_COMMERCIAL_ADS"), "WIDE")
    client.consent.getGeneralConsents()
    client.consent.setGeneralConsents(buildJsonObject { put("general_consent", true) })
    client.content.getFaqContent()
    client.content.getSearchEmptyState()
}

private suspend fun invokeCustomerService(client: PicnicClient) {
    client.customerService.getContactInfo()
    client.customerService.getMessages(listOf("PROMPT", "MESSAGE_BAR"))
    client.customerService.getReminders()
    client.customerService.setReminders(buildJsonArray { add(JsonPrimitive("MONDAY")) })
    client.customerService.getParcels()
    client.customerService.getUnauthenticatedContactInfo("NL")
}

private suspend fun invokeDeliveryAndPayment(client: PicnicClient) {
    client.delivery.getDeliveries(listOf("CURRENT"))
    client.delivery.getDelivery("delivery-1")
    client.delivery.getDeliveryPosition("delivery-1")
    client.delivery.getDeliveryScenario("delivery-1")
    client.delivery.cancelDelivery("delivery-1")
    client.delivery.setDeliveryRating("delivery-1", 10)
    client.delivery.sendDeliveryInvoiceEmail("delivery-1")
    client.payment.getPaymentProfile()
    client.payment.getWalletTransactions(1)
    client.payment.getWalletTransactionDetails("wallet-1")
}

private suspend fun invokeRecipes(client: PicnicClient) {
    client.recipe.getRecipesPage()
    client.recipe.getCookbookPage()
    client.recipe.getRecipeDetailsPage("recipe-1")
    client.recipe.saveRecipe("recipe-1")
    client.recipe.unsaveRecipe("recipe-1")
    client.recipe.assignSellingGroupToBasket("recipe-1", 1, 4)
    client.recipe.updateSellingGroupPortions("recipe-1", 1, 2)
    client.recipe.removeSellingGroupFromBasket("recipe-1")
}

private suspend fun invokeUser(client: PicnicClient) {
    client.user.getUserDetails()
    client.user.getUserInfo()
    client.user.getProfileMenu()
    client.user.submitSuggestion("More vegetables")
    client.user.registerPushToken("push-destination-1", 1)
    client.user.checkForUpdates()
    client.userOnboarding.setHouseholdDetails(buildJsonObject { put("adults", 2) })
    client.userOnboarding.setBusinessDetails(buildJsonObject { put("business_name", "Shop") })
    client.userOnboarding.subscribePush(listOf("delivery"))
    client.auth.generatePhoneVerificationCode("+31600000000")
    client.auth.verifyPhoneNumber("+31600000000", "123456")
    client.auth.logout()
}

private val expectedRoutes = listOf(
    "GET" to "/bootstrap", "GET" to "/pages/home_page_root", "POST" to "/deeplink/resolve",
    "GET" to "/cart", "POST" to "/cart/add_product", "POST" to "/cart/products/add",
    "POST" to "/cart/remove_product", "POST" to "/cart/clear", "GET" to "/cart/delivery_slots",
    "POST" to "/cart/set_delivery_slot", "GET" to "/cart/checkout/order/order-1/status",
    "POST" to "/cart/remove_group", "GET" to "/user-slot-minimum-order-value/minimum",
    "POST" to "/cart/checkout/order/order-1/confirm", "GET" to "/suggest?search_term=milk",
    "GET" to "/pages/search-page-root-content?search_term=milk&search_session_id=11111111-1111-4111-8111-111111111111&pending_search_session_id=11111111-1111-4111-8111-111111111111&is_search_recommendations_active=false&is_text_input_focused=false&force_focus_from_tab=false&skip_initial_search_on_focus=&show_dev_chooser=false",
    "GET" to "/pages/product-details-page-root?id=s1&show_category_action=true&show_remove_from_purchases_page_action=true",
    "GET" to "/static/images/image-1/small.png", "GET" to "/static/images/image-1/large.png",
    "GET" to "/consents/settings-page", "GET" to "/consents/general/settings-page",
    "PUT" to "/consents", "GET" to "/consents?consent_topics=MISC_COMMERCIAL_ADS&strategy=WIDE",
    "GET" to "/consents/general", "PUT" to "/consents/general",
    "GET" to "/content/faq", "GET" to "/content/search_empty_state",
    "GET" to "/cs-contact-info", "GET" to "/messages?display_position=PROMPT&display_position=MESSAGE_BAR",
    "GET" to "/reminders", "PUT" to "/reminders",
    "GET" to "/parcels", "POST" to "/deliveries/summary", "GET" to "/deliveries/delivery-1",
    "GET" to "/deliveries/delivery-1/position", "GET" to "/deliveries/delivery-1/scenario",
    "POST" to "/order/delivery/delivery-1/cancel", "POST" to "/deliveries/delivery-1/rating",
    "POST" to "/deliveries/delivery-1/resend_invoice_email", "GET" to "/payment-profile",
    "POST" to "/wallet/transactions", "GET" to "/wallet/transactions/wallet-1",
    "GET" to "/pages/meals-page-root", "GET" to "/pages/cookbook-page-content",
    "GET" to "/pages/selling-group-details-page?selling_group_id=recipe-1",
    "POST" to "/pages/task/recipe-saving", "POST" to "/pages/task/assign-selling-group-to-basket",
    "POST" to "/pages/task/update-selling-group-number-of-portions-task",
    "POST" to "/pages/task/remove-selling-group-from-basket", "GET" to "/user", "GET" to "/user-info",
    "GET" to "/profile-menu?fetch_mgm=true", "POST" to "/user/suggestion",
    "POST" to "/user/device/register_push", "POST" to "/update_check",
    "POST" to "/user-onboarding/household-details", "POST" to "/user-onboarding/business-details",
    "POST" to "/user-onboarding/subscribe-push", "POST" to "/user/phone_verification/generate",
    "POST" to "/user/phone_verification/verify", "POST" to "/user/logout", "DELETE" to "/custom/route"
)

package com.groceryautomate.picnic

import com.groceryautomate.picnic.adapter.out.crypto.Md5PicnicPasswordHasher
import com.groceryautomate.picnic.adapter.out.http.PicnicRequester
import com.groceryautomate.picnic.adapter.out.id.RandomPicnicIdGenerator
import com.groceryautomate.picnic.adapter.out.memory.InMemoryPicnicAuthStore
import com.groceryautomate.picnic.adapter.out.time.SystemPicnicClock
import com.groceryautomate.picnic.application.port.`in`.PicnicAppPort
import com.groceryautomate.picnic.application.port.`in`.PicnicAuthPort
import com.groceryautomate.picnic.application.port.`in`.PicnicCartPort
import com.groceryautomate.picnic.application.port.`in`.PicnicCatalogPort
import com.groceryautomate.picnic.application.port.`in`.PicnicConsentPort
import com.groceryautomate.picnic.application.port.`in`.PicnicContentPort
import com.groceryautomate.picnic.application.port.`in`.PicnicCustomerServicePort
import com.groceryautomate.picnic.application.port.`in`.PicnicDeliveryPort
import com.groceryautomate.picnic.application.port.`in`.PicnicPaymentPort
import com.groceryautomate.picnic.application.port.`in`.PicnicRawPort
import com.groceryautomate.picnic.application.port.`in`.PicnicRecipePort
import com.groceryautomate.picnic.application.port.`in`.PicnicUserOnboardingPort
import com.groceryautomate.picnic.application.port.`in`.PicnicUserPort
import com.groceryautomate.picnic.application.port.out.PicnicAuthStore
import com.groceryautomate.picnic.application.port.out.PicnicClock
import com.groceryautomate.picnic.application.port.out.PicnicHttpTransport
import com.groceryautomate.picnic.application.port.out.PicnicIdGenerator
import com.groceryautomate.picnic.application.port.out.PicnicPasswordHasher
import com.groceryautomate.picnic.application.service.AppService
import com.groceryautomate.picnic.application.service.AuthService
import com.groceryautomate.picnic.application.service.CartService
import com.groceryautomate.picnic.application.service.CatalogService
import com.groceryautomate.picnic.application.service.ConsentService
import com.groceryautomate.picnic.application.service.ContentService
import com.groceryautomate.picnic.application.service.CustomerService
import com.groceryautomate.picnic.application.service.DeliveryService
import com.groceryautomate.picnic.application.service.PaymentService
import com.groceryautomate.picnic.application.service.RawService
import com.groceryautomate.picnic.application.service.RecipeService
import com.groceryautomate.picnic.application.service.UserOnboardingService
import com.groceryautomate.picnic.application.service.UserService
import com.groceryautomate.picnic.domain.PicnicClientConfig
import kotlinx.serialization.json.Json

class PicnicClient(
    config: PicnicClientConfig = PicnicClientConfig(),
    transport: PicnicHttpTransport,
    authStore: PicnicAuthStore = InMemoryPicnicAuthStore(),
    clock: PicnicClock = SystemPicnicClock,
    passwordHasher: PicnicPasswordHasher = Md5PicnicPasswordHasher(),
    idGenerator: PicnicIdGenerator = RandomPicnicIdGenerator(),
    json: Json = defaultPicnicJson()
) {
    private val requester = PicnicRequester(config, transport, authStore, json)

    val app: PicnicAppPort = AppService(requester)
    val auth: PicnicAuthPort = AuthService(requester, config, authStore, passwordHasher, json)
    val cart: PicnicCartPort = CartService(requester)
    val catalog: PicnicCatalogPort = CatalogService(requester, config, clock, idGenerator)
    val consent: PicnicConsentPort = ConsentService(requester)
    val content: PicnicContentPort = ContentService(requester)
    val customerService: PicnicCustomerServicePort = CustomerService(requester)
    val delivery: PicnicDeliveryPort = DeliveryService(requester)
    val payment: PicnicPaymentPort = PaymentService(requester)
    val recipe: PicnicRecipePort = RecipeService(requester, clock)
    val user: PicnicUserPort = UserService(requester, config)
    val userOnboarding: PicnicUserOnboardingPort = UserOnboardingService(requester)
    val raw: PicnicRawPort = RawService(requester)
}

fun defaultPicnicJson(): Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = false
}

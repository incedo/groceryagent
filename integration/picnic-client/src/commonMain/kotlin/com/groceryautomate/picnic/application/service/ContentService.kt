package com.groceryautomate.picnic.application.service

import com.groceryautomate.picnic.adapter.out.http.PicnicRequester
import com.groceryautomate.picnic.application.port.`in`.PicnicContentPort
import kotlinx.serialization.json.JsonElement

internal class ContentService(
    private val requester: PicnicRequester
) : PicnicContentPort {
    override suspend fun getFaqContent(): JsonElement =
        requester.json("GET", "/content/faq")

    override suspend fun getSearchEmptyState(): JsonElement =
        requester.json("GET", "/content/search_empty_state")
}

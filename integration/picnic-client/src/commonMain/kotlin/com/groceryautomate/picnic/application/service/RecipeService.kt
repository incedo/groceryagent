package com.groceryautomate.picnic.application.service

import com.groceryautomate.picnic.adapter.out.http.PicnicRequester
import com.groceryautomate.picnic.adapter.out.http.encodeQuery
import com.groceryautomate.picnic.application.port.`in`.PicnicRecipePort
import com.groceryautomate.picnic.application.port.out.PicnicClock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class RecipeService(
    private val requester: PicnicRequester,
    private val clock: PicnicClock
) : PicnicRecipePort {
    override suspend fun getRecipesPage(): JsonElement =
        requester.json("GET", "/pages/meals-page-root")

    override suspend fun getCookbookPage(): JsonElement =
        requester.json("GET", "/pages/cookbook-page-content")

    override suspend fun getRecipeDetailsPage(recipeId: String): JsonElement {
        requireId(recipeId, "Recipe")
        return requester.json(
            "GET",
            "/pages/selling-group-details-page?selling_group_id=${encodeQuery(recipeId)}"
        )
    }

    override suspend fun saveRecipe(recipeId: String): JsonElement {
        requireId(recipeId, "Recipe")
        return saveState(recipeId, clock.nowIso8601())
    }

    override suspend fun unsaveRecipe(recipeId: String): JsonElement {
        requireId(recipeId, "Recipe")
        return requester.json(
            "POST",
            "/pages/task/recipe-saving",
            buildJsonObject {
                put("payload", buildJsonObject {
                    put("recipe_id", recipeId)
                    put("saved_at", JsonNull)
                })
            }
        )
    }

    override suspend fun assignSellingGroupToBasket(
        sellingGroupId: String,
        dayOffset: Int?,
        portions: Int?
    ): JsonElement {
        requireId(sellingGroupId, "Selling group")
        dayOffset?.let { require(it >= 0) { "Day offset must not be negative." } }
        portions?.let { require(it > 0) { "Portions must be positive." } }
        return requester.json(
            "POST",
            "/pages/task/assign-selling-group-to-basket",
            payload {
                put("selling_group_id", sellingGroupId)
                dayOffset?.let { put("day_offset", it) }
                portions?.let { put("portions", it) }
            }
        )
    }

    override suspend fun updateSellingGroupPortions(
        sellingGroupId: String,
        dayOffset: Int,
        portions: Int
    ): JsonElement {
        requireId(sellingGroupId, "Selling group")
        require(dayOffset >= 0) { "Day offset must not be negative." }
        require(portions > 0) { "Portions must be positive." }
        return requester.json(
            "POST",
            "/pages/task/update-selling-group-number-of-portions-task",
            payload {
                put("selling_group_id", sellingGroupId)
                put("day_offset", dayOffset)
                put("portions", portions)
            }
        )
    }

    override suspend fun removeSellingGroupFromBasket(sellingGroupId: String): JsonElement {
        requireId(sellingGroupId, "Selling group")
        return requester.json(
            "POST",
            "/pages/task/remove-selling-group-from-basket",
            payload { put("selling_group_id", sellingGroupId) }
        )
    }

    private suspend fun saveState(recipeId: String, savedAt: String): JsonElement {
        require(savedAt.isNotBlank()) { "Clock must return an ISO-8601 timestamp." }
        return requester.json(
            "POST",
            "/pages/task/recipe-saving",
            payload {
                put("recipe_id", recipeId)
                put("saved_at", savedAt)
            }
        )
    }

    private fun payload(content: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) =
        buildJsonObject { put("payload", buildJsonObject(content)) }

    private fun requireId(value: String, label: String) {
        require(value.isNotBlank()) { "$label id must not be blank." }
    }
}

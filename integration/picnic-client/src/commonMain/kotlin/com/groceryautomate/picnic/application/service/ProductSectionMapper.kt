package com.groceryautomate.picnic.application.service

import com.groceryautomate.picnic.domain.PicnicAllergenDataStatus
import com.groceryautomate.picnic.domain.PicnicAllergenStatement
import com.groceryautomate.picnic.domain.PicnicPreparationMethod
import com.groceryautomate.picnic.domain.PicnicPreparationStep
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal fun orderedProductTexts(page: JsonElement): List<String> = buildList {
    page.allObjects().forEach { node ->
        val value = node.stringOrNull("markdown") ?: node.stringOrNull("text")
        value?.let(::stripColorMarkup)?.takeIf(String::isNotBlank)?.let(::add)
    }
}

internal fun extractProductSections(texts: List<String>): Map<String, String> {
    val headers = listOf(
        "Ingrediënten",
        "Bewaren",
        "Productomschrijving",
        "Beschrijving",
        "Land van herkomst",
        "Leverancier"
    )
    return headers.mapNotNull { header -> sectionBody(texts, header)?.let { header to it } }.toMap()
}

internal fun extractAllergens(texts: List<String>, page: JsonElement): PicnicAllergenStatement {
    val contains = allergenValues(texts, setOf("Bevat"))
    val mayContain = allergenValues(
        texts,
        setOf("Bevat mogelijk", "Kan bevatten", "Kan sporen bevatten van")
    )
    val oldBlock = page.findObjectById("product-page-allergies")
        ?.markdowns()
        ?.map { stripMarkdown(stripColorMarkup(it)) }
        .orEmpty()
    val fallback = oldBlock.filterNot { normalizedHeader(it) in allergenHeaders }
    val observed = texts.any { normalizedHeader(it) in allergenHeaders } || oldBlock.isNotEmpty()
    return PicnicAllergenStatement(
        contains = (contains.ifEmpty { fallback }).distinctCaseInsensitive(),
        mayContain = mayContain.distinctCaseInsensitive(),
        status = if (observed) PicnicAllergenDataStatus.OBSERVED else PicnicAllergenDataStatus.UNKNOWN
    )
}

internal fun extractExtraInformation(texts: List<String>): Map<String, String> {
    val start = texts.indexOfFirst { normalizedHeader(it) == "Extra informatie" }
    if (start < 0) return emptyMap()
    val result = linkedMapOf<String, String>()
    var index = start + 1
    while (index < texts.size) {
        val value = texts[index]
        val header = boldHeader(value) ?: break
        val inlineBody = value.substringAfter('\n', "").trim()
        val body = inlineBody.ifBlank { texts.getOrNull(index + 1)?.takeUnless(::isBoldHeader).orEmpty() }
        if (body.isNotBlank() && header !in result) result[header] = body
        index += if (inlineBody.isBlank() && body.isNotBlank()) 2 else 1
    }
    return result
}

internal fun extractPreparation(texts: List<String>): List<PicnicPreparationMethod> {
    val start = texts.indexOfFirst { normalizedHeader(it) in setOf("Bereiding", "Bereidingswijze") }
    if (start < 0) return emptyList()
    val body = buildList {
        texts[start].substringAfter('\n', "").takeIf(String::isNotBlank)?.let(::add)
        for (index in start + 1 until texts.size) {
            if (normalizedHeader(texts[index]) in preparationStopHeaders) break
            add(texts[index])
        }
    }.joinToString("\n").trim()
    if (body.isBlank()) return emptyList()
    val methodPattern = Regex("(?m)^(?!\\*{1,2}Stap)([^\\n:]{2,40}):\\s*$")
    val methods = methodPattern.findAll(body).toList()
    if (methods.isEmpty()) return listOf(PicnicPreparationMethod(null, preparationSteps(body)))
    return methods.mapIndexed { index, match ->
        val end = methods.getOrNull(index + 1)?.range?.first ?: body.length
        PicnicPreparationMethod(match.groupValues[1].trim(), preparationSteps(body.substring(match.range.last + 1, end)))
    }
}

private fun preparationSteps(value: String): List<PicnicPreparationStep> {
    val pattern = Regex("\\*{1,2}\\s*Stap\\s*(\\d+)\\s*\\*{1,2}\\s*(.*?)(?=\\*{1,2}\\s*Stap\\s*\\d+\\s*\\*{1,2}|$)", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    val steps = pattern.findAll(value).map { match ->
        PicnicPreparationStep(match.groupValues[1].toInt(), match.groupValues[2].replace(Regex("\\s+"), " ").trim())
    }.filter { it.text.isNotBlank() }.toList()
    return steps.ifEmpty {
        value.replace(Regex("\\s+"), " ").trim().takeIf(String::isNotBlank)
            ?.let { listOf(PicnicPreparationStep(1, it)) }
            .orEmpty()
    }
}

private fun sectionBody(texts: List<String>, header: String): String? {
    texts.forEachIndexed { index, value ->
        if (normalizedHeader(value.substringBefore('\n')) == header) {
            val inline = value.substringAfter('\n', "").trim()
            return inline.ifBlank { texts.getOrNull(index + 1)?.takeUnless(::isBoldHeader).orEmpty() }
                .takeIf(String::isNotBlank)
        }
    }
    return null
}

private fun allergenValues(texts: List<String>, targets: Set<String>): List<String> {
    val start = texts.indexOfFirst { normalizedHeader(it) in targets }
    if (start < 0) return emptyList()
    return buildList {
        for (index in start + 1 until texts.size) {
            val next = texts[index].trim()
            if (isBoldHeader(next) || normalizedHeader(next) in allergenHeaders || next.length > 60) break
            next.split(',').map(String::trim).filter(String::isNotBlank).forEach(::add)
        }
    }
}

private fun List<String>.distinctCaseInsensitive(): List<String> {
    val seen = mutableSetOf<String>()
    return filter { seen.add(it.lowercase()) }
}

private fun boldHeader(value: String): String? = Regex("^\\*\\*(.+?)\\*\\*").find(value)
    ?.groupValues?.get(1)?.trim()

private fun isBoldHeader(value: String): Boolean = boldHeader(value) != null
private fun normalizedHeader(value: String): String = stripMarkdown(value.substringBefore('\n')).trim()

private val allergenHeaders = setOf("Bevat", "Bevat mogelijk", "Kan bevatten", "Kan sporen bevatten van")
private val preparationStopHeaders = setOf(
    "Ingrediënten", "Voedingswaarde", "Bevat", "Bevat mogelijk", "Extra informatie", "Per 100 g", "Per 100 ml"
)

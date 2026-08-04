package com.groceryautomate.picnic.live

import com.groceryautomate.picnic.domain.PicnicClientConfig
import com.groceryautomate.picnic.domain.PicnicCountry
import java.nio.file.Files
import java.nio.file.Path

internal data class PicnicLiveEnvironment(
    val authToken: String,
    val config: PicnicClientConfig
) {
    companion object {
        fun load(path: Path): PicnicLiveEnvironment {
            require(Files.isRegularFile(path)) {
                "Picnic environment file does not exist: $path"
            }
            val values = parseEnvironment(Files.readAllLines(path))
            val capturedAgent = values["PICNIC_AGENT"]?.trim()?.takeIf(String::isNotEmpty)
            val agentParts = capturedAgent?.let(::parseAgent)
            val clientId = values.positiveIntOrNull("PICNIC_CLIENT_ID")
                ?: agentParts?.clientId
                ?: error("Missing PICNIC_AGENT or PICNIC_CLIENT_ID.")
            val clientVersion = values["PICNIC_CLIENT_VERSION"]?.trim()?.takeIf(String::isNotEmpty)
                ?: agentParts?.clientVersion
                ?: error("Missing PICNIC_AGENT or PICNIC_CLIENT_VERSION.")
            val buildNumber = values.positiveIntOrNull("PICNIC_BUILD_NUMBER")
                ?: agentParts?.buildNumber
                ?: error("Missing PICNIC_AGENT or PICNIC_BUILD_NUMBER.")
            val agent = capturedAgent ?: "$clientId;$clientVersion-#$buildNumber"
            val country = values["PICNIC_COUNTRY"]
                ?: countryFromHost(values["PICNIC_HOST"])
                ?: "nl"
            return PicnicLiveEnvironment(
                authToken = values.required("PICNIC_AUTH"),
                config = PicnicClientConfig(
                    country = PicnicCountry.fromApiCode(country),
                    apiVersion = values.positiveInt("PICNIC_API_VERSION", 15),
                    deviceId = values["PICNIC_DID"] ?: values.required("PICNIC_DEVICE_ID"),
                    clientId = clientId,
                    clientVersion = clientVersion,
                    buildNumber = buildNumber,
                    userAgent = values["PICNIC_UA"] ?: "grocery-automate-picnic-live-smoke/1",
                    agent = agent
                )
            )
        }
    }
}

private data class AgentParts(
    val clientId: Int,
    val clientVersion: String,
    val buildNumber: Int
)

private val agentPattern = Regex("^(\\d+);(.+)-#?(\\d+)$")
private val storefrontHostPattern = Regex("^storefront-prod\\.([a-z]{2})\\.picnicinternational\\.com$")

private fun parseAgent(value: String): AgentParts {
    val match = agentPattern.matchEntire(value)
        ?: error("PICNIC_AGENT must use <client-id>;<version>-<#><build-number>.")
    return AgentParts(
        clientId = match.groupValues[1].toInt(),
        clientVersion = match.groupValues[2],
        buildNumber = match.groupValues[3].toInt()
    )
}

private fun countryFromHost(host: String?): String? = host
    ?.substringAfter("://")
    ?.substringBefore('/')
    ?.let(storefrontHostPattern::matchEntire)
    ?.groupValues
    ?.get(1)

internal fun parseEnvironment(lines: List<String>): Map<String, String> = buildMap {
    lines.forEachIndexed { index, source ->
        val line = source.trim()
        if (line.isEmpty() || line.startsWith('#')) return@forEachIndexed
        val assignment = line.removePrefix("export ")
        val separator = assignment.indexOf('=')
        require(separator > 0) { "Invalid environment assignment on line ${index + 1}." }
        val key = assignment.substring(0, separator).trim()
        require(key.matches(Regex("[A-Z][A-Z0-9_]*"))) {
            "Invalid environment key on line ${index + 1}."
        }
        val rawValue = assignment.substring(separator + 1).trim()
        put(key, rawValue.removeMatchingQuotes())
    }
}

private fun String.removeMatchingQuotes(): String = when {
    length >= 2 && first() == '"' && last() == '"' -> substring(1, lastIndex)
    length >= 2 && first() == '\'' && last() == '\'' -> substring(1, lastIndex)
    else -> this
}

private fun Map<String, String>.required(name: String): String = get(name)
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?: error("Missing required Picnic environment value: $name")

private fun Map<String, String>.positiveInt(name: String, default: Int): Int {
    val value = get(name) ?: return default
    return value.positiveInt(name)
}

private fun Map<String, String>.positiveIntOrNull(name: String): Int? = get(name)?.positiveInt(name)

private fun String.positiveInt(name: String): Int = toIntOrNull()?.takeIf { it > 0 }
    ?: error("$name must be a positive integer.")

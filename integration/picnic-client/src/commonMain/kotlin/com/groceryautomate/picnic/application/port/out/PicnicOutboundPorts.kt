package com.groceryautomate.picnic.application.port.out

data class PicnicHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: ByteArray? = null
)

data class PicnicHttpResponse(
    val statusCode: Int,
    val headers: Map<String, List<String>>,
    val body: ByteArray
) {
    fun header(name: String): String? = headers.entries
        .firstOrNull { it.key.equals(name, ignoreCase = true) }
        ?.value
        ?.firstOrNull()
}

fun interface PicnicHttpTransport {
    suspend fun execute(request: PicnicHttpRequest): PicnicHttpResponse
}

interface PicnicAuthStore {
    fun current(): String?
    fun replace(value: String)
    fun clear()
}

fun interface PicnicClock {
    fun nowIso8601(): String
}

fun interface PicnicPasswordHasher {
    fun hash(password: String): String
}

fun interface PicnicIdGenerator {
    fun newId(): String
}

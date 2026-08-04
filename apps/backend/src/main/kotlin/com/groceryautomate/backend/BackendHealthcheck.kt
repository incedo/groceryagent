package com.groceryautomate.backend

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

internal fun backendHealthcheck(
    host: String = "127.0.0.1",
    port: Int = 8080
): Boolean = runCatching {
    Socket().use { socket ->
        socket.connect(InetSocketAddress(host, port), 1_000)
        socket.soTimeout = 1_000
        socket.getOutputStream().write(
            "GET /health/ready HTTP/1.1\r\nHost: $host\r\nConnection: close\r\n\r\n"
                .toByteArray(StandardCharsets.US_ASCII)
        )
        BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))
            .readLine()
            ?.split(' ')
            ?.getOrNull(1)
            ?.toIntOrNull()
            .let { status -> status != null && status in 200..299 }
    }
}.getOrDefault(false)

package com.groceryautomate.picnic.adapter.out.crypto

import com.groceryautomate.picnic.application.port.out.PicnicPasswordHasher

class Md5PicnicPasswordHasher : PicnicPasswordHasher {
    override fun hash(password: String): String = md5(password.encodeToByteArray())
        .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
}

private fun md5(input: ByteArray): ByteArray {
    val originalBitLength = input.size.toLong() * 8
    val paddedSize = ((input.size + 9 + 63) / 64) * 64
    val message = ByteArray(paddedSize)
    input.copyInto(message)
    message[input.size] = 0x80.toByte()
    for (index in 0 until 8) {
        message[paddedSize - 8 + index] = (originalBitLength ushr (8 * index)).toByte()
    }

    var a0 = 0x67452301
    var b0 = 0xefcdab89.toInt()
    var c0 = 0x98badcfe.toInt()
    var d0 = 0x10325476

    for (offset in message.indices step 64) {
        val words = IntArray(16) { index ->
            val base = offset + index * 4
            (message[base].toInt() and 0xff) or
                ((message[base + 1].toInt() and 0xff) shl 8) or
                ((message[base + 2].toInt() and 0xff) shl 16) or
                ((message[base + 3].toInt() and 0xff) shl 24)
        }
        var a = a0
        var b = b0
        var c = c0
        var d = d0

        for (index in 0 until 64) {
            val (function, wordIndex) = when (index) {
                in 0..15 -> ((b and c) or (b.inv() and d)) to index
                in 16..31 -> ((d and b) or (d.inv() and c)) to ((5 * index + 1) % 16)
                in 32..47 -> (b xor c xor d) to ((3 * index + 5) % 16)
                else -> (c xor (b or d.inv())) to ((7 * index) % 16)
            }
            val previousD = d
            d = c
            c = b
            b += (a + function + constants[index] + words[wordIndex]).rotateLeft(shifts[index])
            a = previousD
        }
        a0 += a
        b0 += b
        c0 += c
        d0 += d
    }
    return intArrayOf(a0, b0, c0, d0).flatMap { value ->
        List(4) { index -> (value ushr (8 * index)).toByte() }
    }.toByteArray()
}

private val shifts = intArrayOf(
    7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
    5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
    4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
    6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21
)

private val constants = IntArray(64) { index ->
    val value = kotlin.math.abs(kotlin.math.sin(index + 1.0)) * 4294967296.0
    value.toLong().toInt()
}

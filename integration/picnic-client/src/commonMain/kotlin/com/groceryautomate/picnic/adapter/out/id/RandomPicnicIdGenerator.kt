package com.groceryautomate.picnic.adapter.out.id

import com.groceryautomate.picnic.application.port.out.PicnicIdGenerator
import kotlin.random.Random

class RandomPicnicIdGenerator(
    private val random: Random = Random.Default
) : PicnicIdGenerator {
    override fun newId(): String {
        val bytes = ByteArray(16).also(random::nextBytes)
        bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x40).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
        val hex = bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        return "${hex.take(8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
            "${hex.substring(16, 20)}-${hex.substring(20)}"
    }
}

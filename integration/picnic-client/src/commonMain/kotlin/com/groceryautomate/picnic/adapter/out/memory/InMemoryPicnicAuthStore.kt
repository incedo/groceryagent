package com.groceryautomate.picnic.adapter.out.memory

import com.groceryautomate.picnic.application.port.out.PicnicAuthStore

class InMemoryPicnicAuthStore(initialAuthKey: String? = null) : PicnicAuthStore {
    private var authKey: String? = initialAuthKey?.trim()?.takeIf(String::isNotEmpty)

    override fun current(): String? = authKey

    override fun replace(value: String) {
        require(value.isNotBlank()) { "Picnic auth key must not be blank." }
        authKey = value
    }

    override fun clear() {
        authKey = null
    }
}

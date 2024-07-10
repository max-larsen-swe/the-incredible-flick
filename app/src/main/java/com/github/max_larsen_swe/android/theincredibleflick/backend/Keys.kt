package com.github.max_larsen_swe.android.theincredibleflick.backend

object Keys {
    lateinit var API: String
    lateinit var SECRET: String
    private var initialized: Boolean = false
    fun initialize(apiKey: String, apiSecret: String) {
        if (initialized)
            return

        API = apiKey
        SECRET = apiSecret

        initialized = true
    }
}
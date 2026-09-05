package com.krishinirnay.core.data.model

/** MOCK is the default, demo-safe mode. LIVE reads real ESP32 data via Firebase. */
enum class AppMode {
    MOCK,
    LIVE,
}

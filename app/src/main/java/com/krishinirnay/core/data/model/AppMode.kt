package com.krishinirnay.core.data.model

/**
 * MOCK is the default, demo-safe mode (narrative-engine sensor data, no
 * network). LIVE polls the FastAPI relay server's `GET /api/latest-sensor`
 * (which the ESP32 posts to via `POST /api/sensor-data`) — see
 * [com.krishinirnay.core.data.network.LiveFieldStateRepositoryImpl]. Not
 * Firebase-backed.
 */
enum class AppMode {
    MOCK,
    LIVE,
}

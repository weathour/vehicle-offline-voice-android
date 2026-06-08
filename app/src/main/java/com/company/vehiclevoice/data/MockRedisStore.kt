package com.company.vehiclevoice.data

class MockRedisStore(initialState: Map<String, String> = emptyMap()) : VehicleStateStore {
    private val state = linkedMapOf<String, String>()

    init {
        state.putAll(initialState)
    }

    override fun put(key: String, value: String) {
        require(key.isNotBlank()) { "state key must not be blank" }
        state[key] = value
    }

    override fun get(key: String): String? = state[key]

    override fun snapshot(): Map<String, String> = state.toMap()

    override fun clear() {
        state.clear()
    }
}

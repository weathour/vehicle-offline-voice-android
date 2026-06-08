package com.company.vehiclevoice.data.readonly

class SimulatedRedisBinaryDataSource(
    initialState: Map<String, ByteArray> = emptyMap(),
    override val sourceName: String = "simulated-redis-protobuf",
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) : BinaryVehicleDataSource {
    private val state = linkedMapOf<String, VehicleBinaryValue>()

    init {
        initialState.forEach { (key, value) -> put(key, value) }
    }

    fun put(key: String, payload: ByteArray) {
        require(key.isNotBlank()) { "Redis key must not be blank" }
        state[key] = VehicleBinaryValue(key = key, payload = payload.copyOf(), updatedAtMs = clockMs())
    }

    override fun read(key: String): VehicleBinaryValue? = state[key]?.copy(payload = state[key]!!.payload.copyOf())

    override fun diagnostics(): VehicleDataSourceDiagnostics = VehicleDataSourceDiagnostics(
        sourceName = sourceName,
        connected = true,
        keyCount = state.size,
        detail = "in-memory Redis-like protobuf fixture"
    )

    fun keys(): Set<String> = state.keys.toSet()
}

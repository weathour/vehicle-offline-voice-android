package com.company.vehiclevoice.data.readonly

enum class VehicleDataSourceMode(val wireValue: String) {
    Simulated("simulated"),
    RemoteRedis("remote_redis");

    companion object {
        fun fromWireValue(value: String?): VehicleDataSourceMode =
            entries.firstOrNull { it.wireValue == value } ?: Simulated
    }
}

data class VehicleDataSourceRuntimeConfig(
    val mode: VehicleDataSourceMode = VehicleDataSourceMode.Simulated,
    val host: String = DEFAULT_REMOTE_HOST,
    val port: Int = DEFAULT_REMOTE_PORT,
    val password: String? = null,
    val database: Int = 0,
    val timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    val snapshotDeadlineMs: Long = DEFAULT_SNAPSHOT_DEADLINE_MS
) {
    val endpoint: String get() = "$host:$port/$database"
    val displayName: String get() = when (mode) {
        VehicleDataSourceMode.Simulated -> "本地模拟Redis"
        VehicleDataSourceMode.RemoteRedis -> "电脑Redis $host:$port/db$database"
    }

    companion object {
        const val DEFAULT_REMOTE_HOST = "192.168.2.112"
        const val DEFAULT_REMOTE_PORT = 6379
        const val DEFAULT_TIMEOUT_MS = 1500
        const val DEFAULT_SNAPSHOT_DEADLINE_MS = 1200L

        const val EXTRA_SOURCE_MODE = "com.company.vehiclevoice.EXTRA_VEHICLE_SOURCE_MODE"
        const val EXTRA_REDIS_HOST = "com.company.vehiclevoice.EXTRA_REDIS_HOST"
        const val EXTRA_REDIS_PORT = "com.company.vehiclevoice.EXTRA_REDIS_PORT"
        const val EXTRA_REDIS_PASSWORD = "com.company.vehiclevoice.EXTRA_REDIS_PASSWORD"
        const val EXTRA_REDIS_DATABASE = "com.company.vehiclevoice.EXTRA_REDIS_DATABASE"
        const val EXTRA_REDIS_TIMEOUT_MS = "com.company.vehiclevoice.EXTRA_REDIS_TIMEOUT_MS"
        const val EXTRA_SNAPSHOT_DEADLINE_MS = "com.company.vehiclevoice.EXTRA_SNAPSHOT_DEADLINE_MS"
    }
}

data class SocketRedisConfig(
    val host: String,
    val port: Int = VehicleDataSourceRuntimeConfig.DEFAULT_REMOTE_PORT,
    val password: String? = null,
    val database: Int = 0,
    val timeoutMs: Int = VehicleDataSourceRuntimeConfig.DEFAULT_TIMEOUT_MS
) {
    init {
        require(host.isNotBlank()) { "Redis host must not be blank" }
        require(port in 1..65535) { "Redis port must be 1..65535" }
        require(database >= 0) { "Redis database must be non-negative" }
        require(timeoutMs in 50..10_000) { "Redis timeoutMs must be 50..10000" }
    }

    val endpoint: String get() = "$host:$port/$database"
}

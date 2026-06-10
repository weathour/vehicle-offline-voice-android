package com.company.vehiclevoice.data.readonly

/**
 * Read-only binary key/value surface that mirrors the real vehicle Redis cache contract.
 *
 * Implementations may be in-memory fixtures, a desktop test bridge, or a real Redis client.
 * Keeping the app code on this small interface preserves a direct switch point between
 * simulated Redis/protobuf and the vehicle-side Redis cache.
 */
interface BinaryVehicleDataSource {
    val sourceName: String
    fun read(key: String): VehicleBinaryValue?
    fun diagnostics(): VehicleDataSourceDiagnostics
}

data class VehicleBinaryValue(
    val key: String,
    val payload: ByteArray,
    val updatedAtMs: Long
)

data class VehicleDataSourceDiagnostics(
    val sourceName: String,
    val connected: Boolean,
    val keyCount: Int,
    val detail: String
)

object VehicleRedisKeys {
    const val SPEED = "BC_Veh_Spd"
    const val DCU_INFO_1 = "DCU_INFO_1"
    const val DCU_INFO_2 = "DCU_INFO_2"
    const val BATTERY = "DCU_Battery_St"
    const val RANGE = "DCU_INFO_St"
    const val RANGE_LEGACY = "DCU_INFO"
    const val L2_STATE = "DCU_L2_St"
    const val BODY_STATE = "BC_AutoD_Veh_St"
    const val TPMS = "TPMS_INFO"
    const val AC_TEMPERATURE = "ACM_INF2"
    const val AC_STATE = "ACM_INF4"
    const val LOCATION = "Sensor_Location"
    const val OBSTACLES = "Sensor_Mmobstacles"
    const val TRAFFIC_LIGHTS = "Sensor_TrafficLightlist"
    const val TRAFFIC_LIGHTS_LEGACY = "Sensor_Trafficlightlist"
    const val LANES = "Sensor_Lanelist"
    const val MAIN_OBSTACLE = "PFC_Main_Obstacle_INF"
    const val PLANNED_TRAJECTORY = "planned_trajectory"

    const val SAM = "Sensor_SAM"
    const val SAM_LEGACY = "Sam"

    val defaultReadOnlyKeys: List<String> = listOf(
        SPEED,
        DCU_INFO_1,
        DCU_INFO_2,
        BATTERY,
        RANGE,
        L2_STATE,
        AC_TEMPERATURE,
        AC_STATE,
        BODY_STATE,
        TPMS,
        LOCATION,
        OBSTACLES,
        TRAFFIC_LIGHTS,
        LANES,
        MAIN_OBSTACLE,
        PLANNED_TRAJECTORY,
        SAM
    )

    val aliases: Map<String, List<String>> = mapOf(
        RANGE to listOf(RANGE_LEGACY),
        TRAFFIC_LIGHTS to listOf(TRAFFIC_LIGHTS_LEGACY),
        SAM to listOf(SAM_LEGACY)
    )
}

package com.company.vehiclevoice.data.readonly

data class VehicleReadOnlySnapshot(
    val sourceName: String,
    val diagnostics: VehicleDataSourceDiagnostics,
    val keyStatuses: Map<String, KeyReadStatus>,
    val speedKmh: Float? = null,
    val gear: String? = null,
    val parking: String? = null,
    val batterySocPercent: Float? = null,
    val remainingRangeKm: Float? = null,
    val acPower: String? = null,
    val acMode: String? = null,
    val acFanGear: Int? = null,
    val acSetTempCelsius: Float? = null,
    val inCarTempCelsius: Float? = null,
    val outCarTempCelsius: Float? = null,
    val frontDoor: String? = null,
    val midDoor: String? = null,
    val horn: String? = null,
    val wiper: String? = null,
    val tireStatus: VehicleTireStatus? = null,
    val dcuInfo2: VehicleDcuInfo2? = null,
    val l2Status: VehicleL2Status? = null,
    val location: VehicleLocation? = null,
    val nearestObstacle: VehicleObstacle? = null,
    val perceptionFaults: VehiclePerceptionFaults? = null,
    val trafficLight: VehicleTrafficLight? = null,
    val cooperativeState: VehicleCooperativeState? = null
) {
    val hasAnyDecodedValue: Boolean get() = listOfNotNull(
        speedKmh,
        gear,
        parking,
        batterySocPercent,
        remainingRangeKm,
        acPower,
        acMode,
        acFanGear,
        acSetTempCelsius,
        inCarTempCelsius,
        outCarTempCelsius,
        frontDoor,
        midDoor,
        horn,
        wiper,
        tireStatus,
        dcuInfo2,
        l2Status,
        location,
        nearestObstacle,
        perceptionFaults,
        trafficLight,
        cooperativeState
    ).isNotEmpty()
}

data class VehicleLocation(
    val timestamp: Double?,
    val lon: Double?,
    val lat: Double?,
    val heading: Double?,
    val linearVelocity: Double?
)

data class VehicleObstacle(
    val id: Int?,
    val type: String?,
    val vehicleX: Double?,
    val vehicleY: Double?,
    val velocity: Double?,
    val confidence: Double?
)

data class VehiclePerceptionFaults(
    val cameraFault: String?,
    val radarFault: String?,
    val vehicleConnectFault: String?,
    val fusionFault: String?
) {
    val summary: String get() = listOfNotNull(cameraFault, radarFault, vehicleConnectFault, fusionFault)
        .filterNot { it == "无故障" }
        .ifEmpty { listOf("无故障") }
        .joinToString("，")
}

data class VehicleTrafficLight(
    val color: String?,
    val confidence: Double?,
    val count: Int?
)

data class VehicleTireStatus(
    val location: String?,
    val pressureKpa: Int?,
    val temperatureCelsius: Float?,
    val highTempAlarm: String?,
    val leakAlarm: String?,
    val lostAlarm: String?,
    val pressureAlarm: String?
) {
    val normal: Boolean get() = listOf(highTempAlarm, leakAlarm, lostAlarm, pressureAlarm).all {
        it == null || it == "正常" || it == "没有压力报警"
    }

    val alarmSummary: String get() = listOfNotNull(highTempAlarm, leakAlarm, lostAlarm, pressureAlarm)
        .filterNot { it == "正常" || it == "没有压力报警" }
        .ifEmpty { listOf("正常") }
        .joinToString("，")
}

data class VehicleDcuInfo2(
    val autoDLimitInReason: String?,
    val emergencyStopReason: String?,
    val lowVoltageFault: String?,
    val takeoverRequest: String?,
    val driveMode: String?,
    val autoDOutReason: String?,
    val brakeSystemFault: String?,
    val brakeStatus: String?,
    val highVoltageFault: String?
) {
    val warningSummary: String get() = listOfNotNull(
        autoDLimitInReason?.takeUnless { it == "无" },
        emergencyStopReason?.takeUnless { it == "无" },
        lowVoltageFault?.takeUnless { it == "无故障" },
        takeoverRequest?.takeUnless { it == "未激活" },
        autoDOutReason?.takeUnless { it == "无" },
        brakeSystemFault?.takeUnless { it == "无故障" },
        highVoltageFault?.takeUnless { it == "无故障" }
    ).ifEmpty { listOf("无明显告警") }.joinToString("，")
}

data class VehicleL2Status(
    val accStatus: String?,
    val accMode: String?,
    val accFailReason: String?,
    val accQuitReason: String?,
    val lkaStatus: String?,
    val lkaQuitReason: String?,
    val lkaFailReason: String?,
    val l2ActiveMode: String?
) {
    val summary: String get() = listOfNotNull(
        accStatus?.let { "ACC$it" },
        lkaStatus?.let { "LKA$it" },
        l2ActiveMode
    ).ifEmpty { listOf("暂未读取到 L2 状态") }.joinToString("，")
}

data class VehicleCooperativeState(
    val timestamp: Double?,
    val sceneId: Int?,
    val sceneName: String?,
    val eventType: String?,
    val vehicleId: Int?,
    val vehicleNumber: String?,
    val autoLevel: String?,
    val speedMps: Double?,
    val collaborativeVehicleCount: Int?,
    val drivingIntention: String?,
    val intentReason: String?,
    val guideDecision: String?,
    val feedbackResult: String?,
    val coordinateBehavior: String?,
    val checkCount: Int?
) {
    val v2xType: String? get() = sceneId?.let {
        when (it) {
            0 -> "无协作"
            in 1..7 -> "V2V"
            in 8..13 -> "V2I"
            else -> "未知协作类型"
        }
    }

    val summary: String get() = listOfNotNull(
        sceneName,
        eventType,
        collaborativeVehicleCount?.let { "协作车${it}辆" }
    ).ifEmpty { listOf("无协作场景") }.joinToString("，")
}

data class KeyReadStatus(
    val key: String,
    val present: Boolean,
    val decoded: Boolean,
    val updatedAtMs: Long? = null,
    val error: String? = null
)

interface VehicleReadOnlySnapshotProvider {
    val providerName: String
    fun readSnapshot(): VehicleReadOnlySnapshot
}

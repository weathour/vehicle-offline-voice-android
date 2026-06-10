package com.company.vehiclevoice.data.readonly

class RedisVehicleSnapshotProvider(
    private val dataSource: BinaryVehicleDataSource,
    private val keys: List<String> = VehicleRedisKeys.defaultReadOnlyKeys,
    private val snapshotDeadlineMs: Long = Long.MAX_VALUE,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) : VehicleReadOnlySnapshotProvider {
    override val providerName: String = dataSource.sourceName

    override fun readSnapshot(): VehicleReadOnlySnapshot {
        val statuses = linkedMapOf<String, KeyReadStatus>()
        val startedAtMs = clockMs()
        var speed: Float? = null
        var speedSource: String? = null
        var gear: String? = null
        var parking: String? = null
        var batterySoc: Float? = null
        var batteryVoltage: Float? = null
        var batteryCurrent: Float? = null
        var range: Float? = null
        var acPower: String? = null
        var acMode: String? = null
        var acFanGear: Int? = null
        var acSetTemp: Float? = null
        var inCarTemp: Float? = null
        var outCarTemp: Float? = null
        var frontDoor: String? = null
        var midDoor: String? = null
        var horn: String? = null
        var wiper: String? = null
        var tireStatus: VehicleTireStatus? = null
        var dcuInfo2: VehicleDcuInfo2? = null
        var l2Status: VehicleL2Status? = null
        var location: VehicleLocation? = null
        var obstacleCount: Int? = null
        var obstacles: List<VehicleObstacle> = emptyList()
        var nearestObstacle: VehicleObstacle? = null
        var perceptionFaults: VehiclePerceptionFaults? = null
        var trafficLight: VehicleTrafficLight? = null
        var laneStatus: VehicleLaneStatus? = null
        var plannedTrajectory: VehiclePlannedTrajectory? = null
        var cooperativeState: VehicleCooperativeState? = null

        for ((index, key) in keys.withIndex()) {
            val remaining = remainingDeadlineMs(startedAtMs)
            if (remaining <= 0L) {
                keys.drop(index).forEach { remainingKey ->
                    statuses[remainingKey] = KeyReadStatus(
                        key = remainingKey,
                        present = false,
                        decoded = false,
                        error = "snapshot_deadline_exceeded"
                    )
                }
                break
            }

            val value = try {
                readWithDeadline(key, startedAtMs)
            } catch (throwable: Throwable) {
                statuses[key] = KeyReadStatus(
                    key = key,
                    present = false,
                    decoded = false,
                    error = throwable.message ?: throwable::class.java.simpleName
                )
                continue
            }
            if (value == null) {
                statuses[key] = KeyReadStatus(key = key, present = false, decoded = false, error = "missing")
                continue
            }
            runCatching {
                when (key) {
                    VehicleRedisKeys.SPEED -> {
                        if (speed == null) {
                            val decodedSpeed = VehicleInterfaceProto.decodeSpeed(value.payload)
                            if (decodedSpeed == null) {
                                error("business_value_missing_or_timestamp_only")
                            }
                            speed = decodedSpeed
                            speedSource = "BC_Veh_Spd"
                        }
                    }
                    VehicleRedisKeys.DCU_INFO_1 -> VehicleInterfaceProto.decodeDcuInfo1(value.payload).also {
                        gear = it.gear
                        parking = it.parking
                    }
                    VehicleRedisKeys.DCU_INFO_2 -> dcuInfo2 = VehicleInterfaceProto.decodeDcuInfo2(value.payload)
                    VehicleRedisKeys.BATTERY -> VehicleInterfaceProto.decodeBattery(value.payload).also {
                        batteryVoltage = it.voltage
                        batteryCurrent = it.current
                        batterySoc = it.socPercent
                    }
                    VehicleRedisKeys.RANGE -> range = VehicleInterfaceProto.decodeRange(value.payload)
                    VehicleRedisKeys.L2_STATE -> l2Status = VehicleInterfaceProto.decodeL2Status(value.payload)
                    VehicleRedisKeys.AC_TEMPERATURE -> VehicleInterfaceProto.decodeAcTemperature(value.payload).also {
                        inCarTemp = it.inCar
                        outCarTemp = it.outCar
                    }
                    VehicleRedisKeys.AC_STATE -> VehicleInterfaceProto.decodeAcState(value.payload).also {
                        acPower = it.power
                        acMode = it.mode
                        acFanGear = it.fanGear
                        acSetTemp = it.setTemp
                    }
                    VehicleRedisKeys.BODY_STATE -> VehicleInterfaceProto.decodeBodyState(value.payload).also {
                        frontDoor = it.frontDoor
                        midDoor = it.midDoor
                        horn = it.horn
                        wiper = it.wiper
                    }
                    VehicleRedisKeys.TPMS -> tireStatus = VehicleInterfaceProto.decodeTpms(value.payload)
                    VehicleRedisKeys.LOCATION -> {
                        location = VehicleInterfaceProto.decodeLocation(value.payload)
                        location?.linearVelocity?.let {
                            speed = (it * 3.6).toFloat()
                            speedSource = "Sensor_Location.linear_velocity"
                        }
                    }
                    VehicleRedisKeys.OBSTACLES -> VehicleInterfaceProto.decodeObstacles(value.payload).also {
                        obstacleCount = it.count
                        obstacles = it.obstacles
                        nearestObstacle = it.nearest ?: nearestObstacle
                    }
                    VehicleRedisKeys.TRAFFIC_LIGHTS -> trafficLight = VehicleInterfaceProto.decodeTrafficLights(value.payload)
                    VehicleRedisKeys.LANES -> laneStatus = VehicleInterfaceProto.decodeLaneStatus(value.payload)
                    VehicleRedisKeys.MAIN_OBSTACLE -> VehicleInterfaceProto.decodeMainObstacle(value.payload).also {
                        if (nearestObstacle == null) nearestObstacle = it.obstacle
                        perceptionFaults = it.faults
                    }
                    VehicleRedisKeys.PLANNED_TRAJECTORY -> plannedTrajectory = VehicleInterfaceProto.decodePlannedTrajectory(value.payload)
                    VehicleRedisKeys.SAM -> cooperativeState = VehicleInterfaceProto.decodeSam(value.payload)
                }
            }.onSuccess {
                statuses[key] = KeyReadStatus(key = key, present = true, decoded = true, updatedAtMs = value.updatedAtMs)
            }.onFailure { throwable ->
                statuses[key] = KeyReadStatus(
                    key = key,
                    present = true,
                    decoded = false,
                    updatedAtMs = value.updatedAtMs,
                    error = throwable.message ?: throwable::class.java.simpleName
                )
            }
        }

        return VehicleReadOnlySnapshot(
            sourceName = dataSource.sourceName,
            diagnostics = dataSource.diagnostics(),
            keyStatuses = statuses,
            speedKmh = speed,
            speedSource = speedSource,
            gear = gear,
            parking = parking,
            batterySocPercent = batterySoc,
            batteryVoltageVolts = batteryVoltage,
            batteryCurrentAmps = batteryCurrent,
            remainingRangeKm = range,
            acPower = acPower,
            acMode = acMode,
            acFanGear = acFanGear,
            acSetTempCelsius = acSetTemp,
            inCarTempCelsius = inCarTemp,
            outCarTempCelsius = outCarTemp,
            frontDoor = frontDoor,
            midDoor = midDoor,
            horn = horn,
            wiper = wiper,
            tireStatus = tireStatus,
            dcuInfo2 = dcuInfo2,
            l2Status = l2Status,
            location = location,
            obstacleCount = obstacleCount,
            nearestObstacle = nearestObstacle,
            obstacles = obstacles,
            perceptionFaults = perceptionFaults,
            trafficLight = trafficLight,
            laneStatus = laneStatus,
            plannedTrajectory = plannedTrajectory,
            cooperativeState = cooperativeState
        )
    }

    private fun remainingDeadlineMs(startedAtMs: Long): Long {
        if (snapshotDeadlineMs == Long.MAX_VALUE) return Long.MAX_VALUE
        return snapshotDeadlineMs - (clockMs() - startedAtMs)
    }

    private fun readWithDeadline(key: String, startedAtMs: Long): VehicleBinaryValue? {
        for (candidate in listOf(key) + VehicleRedisKeys.aliases[key].orEmpty()) {
            val remainingMs = remainingDeadlineMs(startedAtMs)
            if (remainingMs <= 0L) error("snapshot_deadline_exceeded")
            val value = if (dataSource is SocketRedisBinaryDataSource && remainingMs != Long.MAX_VALUE) {
                dataSource.read(candidate, timeoutMsOverride = remainingMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            } else {
                dataSource.read(candidate)
            }
            if (value != null) return value.copy(key = key)
        }
        return null
    }

    companion object {
        fun simulated(clockMs: () -> Long = { System.currentTimeMillis() }): RedisVehicleSnapshotProvider =
            RedisVehicleSnapshotProvider(SimulatedVehicleRedisFixtures.defaultSource(clockMs), clockMs = clockMs)
    }
}

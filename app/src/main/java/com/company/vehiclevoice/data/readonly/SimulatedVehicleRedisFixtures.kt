package com.company.vehiclevoice.data.readonly

object SimulatedVehicleRedisFixtures {
    fun defaultSource(clockMs: () -> Long = { System.currentTimeMillis() }): SimulatedRedisBinaryDataSource =
        SimulatedRedisBinaryDataSource(defaultPayloads(), clockMs = clockMs)

    fun defaultPayloads(): Map<String, ByteArray> = linkedMapOf(
        VehicleRedisKeys.SPEED to speed(12.5f),
        VehicleRedisKeys.DCU_INFO_1 to dcuInfo1(gear = 3, parking = 0),
        VehicleRedisKeys.DCU_INFO_2 to dcuInfo2(
            autoDLimitInReason = 0,
            emergencyStopReason = 0,
            lowFault = 0,
            takeoverRequest = 0,
            driveMode = 4,
            autoDOutReason = 0,
            brakeFault = 0,
            brakeStatus = 0,
            highFault = 0
        ),
        VehicleRedisKeys.BATTERY to battery(socPercent = 76.0f),
        VehicleRedisKeys.RANGE to range(128.0f),
        VehicleRedisKeys.L2_STATE to l2State(accStatus = 4, accMode = 2, lkaStatus = 3, l2Mode = 3),
        VehicleRedisKeys.AC_TEMPERATURE to acTemperature(inCar = 26.5f, outCar = 30.0f),
        VehicleRedisKeys.AC_STATE to acState(power = 1, mode = 4, fanGear = 2, setTemp = 24.0f),
        VehicleRedisKeys.BODY_STATE to bodyState(frontDoor = 2, midDoor = 2),
        VehicleRedisKeys.TPMS to tpms(pressureKpa = 830, tempCelsius = 36.0f),
        VehicleRedisKeys.LOCATION to location(),
        VehicleRedisKeys.OBSTACLES to obstacles(),
        VehicleRedisKeys.TRAFFIC_LIGHTS to trafficLights(color = 3),
        VehicleRedisKeys.LANES to laneList(),
        VehicleRedisKeys.MAIN_OBSTACLE to mainObstacle(),
        VehicleRedisKeys.PLANNED_TRAJECTORY to plannedTrajectory(),
        VehicleRedisKeys.SAM to sam(sceneId = 1, eventType = 2, collaborativeVehicleCount = 2)
    )

    fun speed(value: Float): ByteArray = ProtoWire.build { float(1, value) }

    fun dcuInfo1(gear: Int, parking: Int): ByteArray = ProtoWire.build {
        enum(1, gear)
        enum(2, 4)
        enum(3, parking)
        enum(4, 0)
    }

    fun dcuInfo2(
        autoDLimitInReason: Int = 0,
        emergencyStopReason: Int = 0,
        lowFault: Int = 0,
        takeoverRequest: Int = 0,
        driveMode: Int = 4,
        autoDOutReason: Int = 0,
        brakeFault: Int = 0,
        brakeStatus: Int = 0,
        highFault: Int = 0
    ): ByteArray = ProtoWire.build {
        enum(1, autoDLimitInReason)
        enum(2, emergencyStopReason)
        enum(3, lowFault)
        enum(4, takeoverRequest)
        enum(5, driveMode)
        enum(6, autoDOutReason)
        enum(9, brakeFault)
        enum(10, brakeStatus)
        enum(11, highFault)
    }

    fun battery(socPercent: Float): ByteArray = ProtoWire.build {
        double(1, 1_717_820_800.0)
        float(2, 612.0f)
        float(3, 8.5f)
        float(4, socPercent)
    }

    fun range(km: Float): ByteArray = ProtoWire.build {
        double(1, 1_717_820_800.0)
        float(2, km)
    }

    fun l2State(
        accStatus: Int = 4,
        accMode: Int = 2,
        accFailReason: Int = 0,
        accQuitReason: Int = 0,
        lkaStatus: Int = 3,
        lkaQuitReason: Int = 0,
        lkaFailReason: Int = 0,
        l2Mode: Int = 3
    ): ByteArray = ProtoWire.build {
        enum(1, accStatus)
        enum(2, accMode)
        enum(3, accFailReason)
        enum(4, accQuitReason)
        enum(5, lkaStatus)
        enum(6, lkaQuitReason)
        enum(7, lkaFailReason)
        enum(9, l2Mode)
    }

    fun acTemperature(inCar: Float, outCar: Float): ByteArray = ProtoWire.build {
        float(1, inCar)
        float(2, outCar)
    }

    fun acState(power: Int, mode: Int, fanGear: Int, setTemp: Float): ByteArray = ProtoWire.build {
        enum(1, power)
        enum(2, mode)
        enum(3, fanGear)
        float(4, setTemp)
    }

    fun bodyState(frontDoor: Int = 2, midDoor: Int = 2, horn: Int = 0, wiper: Int = 0): ByteArray = ProtoWire.build {
        enum(3, frontDoor)
        enum(4, midDoor)
        enum(8, horn)
        enum(23, wiper)
    }

    fun tpms(
        location: Int = 0,
        pressureKpa: Int = 830,
        tempCelsius: Float = 36.0f,
        highTempAlarm: Int = 0,
        leakAlarm: Int = 0,
        lostAlarm: Int = 0,
        pressureAlarm: Int = 2
    ): ByteArray = ProtoWire.build {
        enum(1, location)
        uint32(2, pressureKpa)
        float(3, tempCelsius)
        enum(4, highTempAlarm)
        enum(5, leakAlarm)
        enum(6, lostAlarm)
        enum(7, pressureAlarm)
    }

    fun location(): ByteArray = ProtoWire.build {
        double(1, 1_717_820_800.0)
        double(2, 106.5516)
        double(3, 29.5630)
        double(4, 302.8)
        double(5, -0.02)
        double(6, 0.01)
        double(7, 92.0)
        double(8, 12.5 / 3.6)
        double(9, 3.4)
        double(10, 0.2)
        double(11, 0.0)
        double(12, 0.3)
        double(16, 0.002)
        double(22, -5714.3)
        double(23, 579.0)
        double(24, 302.8)
        int32(25, 1)
    }

    fun obstacles(type: Int = 2, x: Double = 18.2, y: Double = -0.5, confidence: Double = 0.88): ByteArray = ProtoWire.build {
        double(1, 1_717_820_800.0)
        int32(2, 2)
        message(3, ProtoWire.build {
            int32(1, 101)
            double(2, x)
            double(3, y)
            double(4, 0.4)
            double(9, 2.1)
            double(12, 4.6)
            double(13, 1.8)
            double(14, 1.6)
            int32(15, type)
            double(16, confidence)
            double(23, 106.5517)
            double(24, 29.5631)
        })
        message(3, ProtoWire.build {
            int32(1, 102)
            double(2, 28.0)
            double(3, 1.0)
            double(9, 0.5)
            int32(15, 2)
            double(16, 0.72)
        })
    }

    fun trafficLights(color: Int = 3, confidence: Double = 0.93): ByteArray = ProtoWire.build {
        double(1, 1_717_820_800.0)
        int32(2, 1)
        message(3, ProtoWire.build {
            int32(1, color)
            double(2, confidence)
            int32(3, 120)
            int32(4, 48)
            int32(5, 32)
            int32(6, 18)
        })
    }

    fun trafficLightsTimestampOnly(): ByteArray = ProtoWire.build {
        double(1, 1_717_820_800.0)
    }

    fun laneList(count: Int = 2, confidence: Double = 0.82): ByteArray = ProtoWire.build {
        double(1, 1_717_820_800.0)
        int32(2, count)
        message(3, ProtoWire.build {
            int32(1, 1)
            int32(2, 2)
            double(6, confidence)
        })
    }

    fun laneListTimestampOnly(): ByteArray = ProtoWire.build {
        double(1, 1_717_820_800.0)
    }

    fun plannedTrajectory(): ByteArray =
        "[[-2709.7, 467.3], [-2708.7, 467.3], [-2690.2, 464.0]]".toByteArray(Charsets.UTF_8)

    fun mainObstacle(
        type: Int = 2,
        x: Float = 18.2f,
        y: Float = -0.5f,
        relativeVelocityX: Float = 2.1f,
        cameraFault: Int = 0,
        radarFault: Int = 0,
        vehicleConnectFault: Int = 0,
        fusionFault: Int = 0
    ): ByteArray = ProtoWire.build {
        enum(1, type)
        float(2, x)
        float(3, y)
        float(4, relativeVelocityX)
        float(5, 0.0f)
        enum(6, cameraFault)
        enum(7, radarFault)
        enum(8, vehicleConnectFault)
        enum(9, fusionFault)
    }

    fun sam(
        sceneId: Int = 1,
        eventType: Int = 2,
        collaborativeVehicleCount: Int = 2,
        guideDecision: Int = 1,
        feedbackResult: Int = 3,
        coordinateBehavior: Int = 1
    ): ByteArray = ProtoWire.build {
        double(1, 1_717_820_800.0)
        int32(2, sceneId)
        int32(3, 1001)
        string(4, "渝A-SIM01")
        string(5, "L4")
        int32(6, 4)
        int32(7, 3)
        double(8, 1.5)
        double(9, 0.2)
        double(10, 3.47)
        int32(11, collaborativeVehicleCount)
        message(12, ProtoWire.build {
            int32(1, 2001)
            int32(2, 1)
        })
        int32(13, 1)
        int32(14, 4)
        int32(15, guideDecision)
        int32(16, feedbackResult)
        int32(17, coordinateBehavior)
        enum(18, eventType)
        double(19, 1_717_820_700_000.0)
        double(20, 0.0)
        int32(21, 42)
    }
}

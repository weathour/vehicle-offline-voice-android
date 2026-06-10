package com.company.vehiclevoice.data.readonly

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedisVehicleSnapshotProviderTest {
    @Test
    fun simulatedRedisProtobuf_decodesDefaultVehicleSnapshot() {
        val provider = RedisVehicleSnapshotProvider.simulated(clockMs = { 1234L })

        val snapshot = provider.readSnapshot()

        assertEquals("simulated-redis-protobuf", snapshot.sourceName)
        assertTrue(snapshot.diagnostics.connected)
        assertEquals(12.5f, snapshot.speedKmh!!, 0.01f)
        assertEquals("D", snapshot.gear)
        assertEquals("释放", snapshot.parking)
        assertEquals(76.0f, snapshot.batterySocPercent!!, 0.01f)
        assertEquals(612.0f, snapshot.batteryVoltageVolts!!, 0.01f)
        assertEquals(8.5f, snapshot.batteryCurrentAmps!!, 0.01f)
        assertEquals("Sensor_Location.linear_velocity", snapshot.speedSource)
        assertEquals("开启", snapshot.acPower)
        assertEquals("制冷", snapshot.acMode)
        assertEquals("已关闭", snapshot.frontDoor)
        assertEquals("正常", snapshot.tireStatus!!.alarmSummary)
        assertEquals("自动驾驶模式", snapshot.dcuInfo2!!.driveMode)
        assertEquals("ACC激活，LKA激活，L2功能激活", snapshot.l2Status!!.summary)
        assertEquals("无故障", snapshot.perceptionFaults!!.summary)
        assertEquals("绿灯", snapshot.trafficLight!!.color)
        assertTrue(snapshot.trafficLight!!.hasBusinessData)
        assertEquals(2, snapshot.obstacleCount)
        assertEquals("车辆", snapshot.nearestObstacle!!.type)
        assertEquals(101, snapshot.nearestObstacle!!.id)
        assertEquals(2, snapshot.obstacles.size)
        assertEquals(2, snapshot.laneStatus!!.laneCount)
        assertEquals(3, snapshot.plannedTrajectory!!.pointCount)
        assertEquals("V2V协作式变道", snapshot.cooperativeState!!.sceneName)
        assertEquals("进行中", snapshot.cooperativeState!!.eventType)
        assertEquals(2, snapshot.cooperativeState!!.collaborativeVehicleCount)
        assertEquals(4, snapshot.cooperativeState!!.drivingModeFeedback)
        assertTrue(snapshot.keyStatuses.values.all { it.decoded })
    }

    @Test
    fun snapshotStateMapper_exportsStableStringKeysForVoiceReplies() {
        val snapshot = RedisVehicleSnapshotProvider.simulated(clockMs = { 1234L }).readSnapshot()

        val map = VehicleSnapshotStateMapper.toStateMap(snapshot)

        assertEquals("12.5", map["vehicle.speed_kmh"])
        assertEquals("Sensor_Location.linear_velocity", map["vehicle.speed_source"])
        assertEquals("76.0", map["vehicle.battery_soc_percent"])
        assertEquals("612.0", map["vehicle.battery_voltage_v"])
        assertEquals("8.5", map["vehicle.battery_current_a"])
        assertEquals("开启", map["vehicle.ac_power"])
        assertEquals("106.551600", map["vehicle.location_lon"])
        assertEquals("1", map["vehicle.rtk_flag"])
        assertEquals("2", map["vehicle.obstacle_count"])
        assertEquals("3", map["vehicle.trajectory.point_count"])
        assertEquals("绿灯", map["vehicle.traffic_light"])
        assertEquals("true", map["vehicle.lane.business_data"])
        assertEquals("true", map["vehicle.tire.normal"])
        assertEquals("激活", map["vehicle.acc.status"])
        assertEquals("激活", map["vehicle.lka.status"])
        assertEquals("V2V协作式变道", map["vehicle.cooperation.scene"])
        assertTrue(map["vehicle.summary.basic"]!!.contains("车速12.5km/h"))
        assertTrue(map["vehicle.summary.cooperation"]!!.contains("协作车2辆"))
    }

    @Test
    fun provider_marksMalformedPayloadWithoutCrashingWholeSnapshot() {
        val source = SimulatedRedisBinaryDataSource(
            mapOf(
                VehicleRedisKeys.SPEED to byteArrayOf(0x0d, 0x01),
                VehicleRedisKeys.BATTERY to SimulatedVehicleRedisFixtures.defaultPayloads().getValue(VehicleRedisKeys.BATTERY)
            ),
            clockMs = { 1L }
        )

        val snapshot = RedisVehicleSnapshotProvider(source, keys = listOf(VehicleRedisKeys.SPEED, VehicleRedisKeys.BATTERY))
            .readSnapshot()

        assertEquals(76.0f, snapshot.batterySocPercent!!, 0.01f)
        assertEquals(false, snapshot.keyStatuses.getValue(VehicleRedisKeys.SPEED).decoded)
        assertTrue(snapshot.keyStatuses.getValue(VehicleRedisKeys.SPEED).error!!.contains("truncated"))
    }

    @Test
    fun warningFixtures_decodeHumanReadableGroupBFields() {
        val source = SimulatedRedisBinaryDataSource(
            mapOf(
                VehicleRedisKeys.DCU_INFO_2 to SimulatedVehicleRedisFixtures.dcuInfo2(
                    autoDLimitInReason = 32,
                    emergencyStopReason = 5,
                    lowFault = 2,
                    takeoverRequest = 1,
                    driveMode = 4,
                    autoDOutReason = 10,
                    brakeFault = 1,
                    highFault = 1
                ),
                VehicleRedisKeys.L2_STATE to SimulatedVehicleRedisFixtures.l2State(
                    accStatus = 7,
                    accFailReason = 9,
                    lkaStatus = 5,
                    lkaFailReason = 12
                ),
                VehicleRedisKeys.TPMS to SimulatedVehicleRedisFixtures.tpms(leakAlarm = 1, pressureAlarm = 3),
                VehicleRedisKeys.MAIN_OBSTACLE to SimulatedVehicleRedisFixtures.mainObstacle(cameraFault = 1, radarFault = 3, fusionFault = 1),
                VehicleRedisKeys.SAM to SimulatedVehicleRedisFixtures.sam(sceneId = 11, eventType = 1, collaborativeVehicleCount = 3)
            ),
            clockMs = { 1L }
        )

        val map = VehicleSnapshotStateMapper.toStateMap(RedisVehicleSnapshotProvider(source).readSnapshot())

        assertEquals("门未关闭", map["vehicle.autod.limit_reason"])
        assertEquals("前碰撞触发", map["vehicle.emergency_stop_reason"])
        assertEquals("激活提醒", map["vehicle.takeover_request"])
        assertEquals("故障", map["vehicle.acc.status"])
        assertEquals("车道宽度不满足", map["vehicle.acc.fail_reason"])
        assertEquals("故障", map["vehicle.lka.status"])
        assertEquals("车道线置信度不满足", map["vehicle.lka.fail_reason"])
        assertFalse(map["vehicle.tire.alarm_summary"] == "正常")
        assertEquals("摄像头连接故障，雷达堵塞故障，感知融合故障", map["vehicle.perception.fault_summary"])
        assertEquals("V2I动态车速限制", map["vehicle.cooperation.scene"])
        assertEquals("开始", map["vehicle.cooperation.event"])
    }

    @Test
    fun providerReadsLegacyAliasesForRenamedVehicleKeys() {
        val source = SimulatedRedisBinaryDataSource(
            mapOf(
                VehicleRedisKeys.RANGE_LEGACY to SimulatedVehicleRedisFixtures.range(88.0f),
                VehicleRedisKeys.TRAFFIC_LIGHTS_LEGACY to SimulatedVehicleRedisFixtures.trafficLights(color = 2),
                VehicleRedisKeys.SAM_LEGACY to SimulatedVehicleRedisFixtures.sam(sceneId = 12, eventType = 1, collaborativeVehicleCount = 4)
            ),
            clockMs = { 7L }
        )

        val snapshot = RedisVehicleSnapshotProvider(
            dataSource = source,
            keys = listOf(VehicleRedisKeys.RANGE, VehicleRedisKeys.TRAFFIC_LIGHTS, VehicleRedisKeys.SAM)
        ).readSnapshot()

        assertEquals(88.0f, snapshot.remainingRangeKm!!, 0.01f)
        assertEquals("黄灯", snapshot.trafficLight!!.color)
        assertEquals("V2I编队行驶", snapshot.cooperativeState!!.sceneName)
        assertTrue(snapshot.keyStatuses.getValue(VehicleRedisKeys.RANGE).decoded)
        assertTrue(snapshot.keyStatuses.getValue(VehicleRedisKeys.TRAFFIC_LIGHTS).decoded)
        assertTrue(snapshot.keyStatuses.getValue(VehicleRedisKeys.SAM).decoded)
    }

    @Test
    fun timestampOnlyTrafficAndLaneAreMarkedWithoutBusinessData() {
        val source = SimulatedRedisBinaryDataSource(
            mapOf(
                VehicleRedisKeys.TRAFFIC_LIGHTS to SimulatedVehicleRedisFixtures.trafficLightsTimestampOnly(),
                VehicleRedisKeys.LANES to SimulatedVehicleRedisFixtures.laneListTimestampOnly()
            ),
            clockMs = { 9L }
        )

        val snapshot = RedisVehicleSnapshotProvider(
            dataSource = source,
            keys = listOf(VehicleRedisKeys.TRAFFIC_LIGHTS, VehicleRedisKeys.LANES)
        ).readSnapshot()
        val map = VehicleSnapshotStateMapper.toStateMap(snapshot)

        assertFalse(snapshot.trafficLight!!.hasBusinessData)
        assertFalse(snapshot.laneStatus!!.hasBusinessData)
        assertEquals("false", map["vehicle.traffic_light.business_data"])
        assertEquals("false", map["vehicle.lane.business_data"])
        assertTrue(map["vehicle.snapshot.timestamp_only_keys"]!!.contains(VehicleRedisKeys.TRAFFIC_LIGHTS))
        assertTrue(map["vehicle.snapshot.timestamp_only_keys"]!!.contains(VehicleRedisKeys.LANES))
    }

    @Test
    fun timestampOnlySpeedKeyDoesNotHideLocationSpeedSource() {
        val source = SimulatedRedisBinaryDataSource(
            mapOf(
                VehicleRedisKeys.SPEED to ProtoWire.build { double(1, 1_717_820_800.0) },
                VehicleRedisKeys.LOCATION to SimulatedVehicleRedisFixtures.location()
            ),
            clockMs = { 10L }
        )

        val snapshot = RedisVehicleSnapshotProvider(
            dataSource = source,
            keys = listOf(VehicleRedisKeys.SPEED, VehicleRedisKeys.LOCATION)
        ).readSnapshot()
        val map = VehicleSnapshotStateMapper.toStateMap(snapshot)

        assertEquals("Sensor_Location.linear_velocity", snapshot.speedSource)
        assertEquals(false, snapshot.keyStatuses.getValue(VehicleRedisKeys.SPEED).decoded)
        assertEquals("business_value_missing_or_timestamp_only", snapshot.keyStatuses.getValue(VehicleRedisKeys.SPEED).error)
        assertTrue(map["vehicle.snapshot.timestamp_only_keys"]!!.contains(VehicleRedisKeys.SPEED))
    }

    @Test
    fun samWithoutSceneIdSummarizesEvidenceAsInsufficient() {
        val source = SimulatedRedisBinaryDataSource(
            mapOf(
                VehicleRedisKeys.SAM to ProtoWire.build {
                    double(1, 1_717_820_800.0)
                    string(5, "L4")
                    double(10, 3.47)
                }
            ),
            clockMs = { 11L }
        )

        val snapshot = RedisVehicleSnapshotProvider(dataSource = source, keys = listOf(VehicleRedisKeys.SAM)).readSnapshot()

        assertTrue(snapshot.cooperativeState!!.summary.contains("未上报scene_id"))
        assertTrue(snapshot.cooperativeState!!.summary.contains("不能判断V2V/V2I"))
    }

    @Test
    fun providerStopsReadingRemainingKeysWhenSnapshotDeadlineIsExceeded() {
        var now = 0L
        val source = object : BinaryVehicleDataSource {
            val requested = mutableListOf<String>()
            override val sourceName: String = "slow-fake"
            override fun read(key: String): VehicleBinaryValue? {
                requested += key
                now += 100L
                return VehicleBinaryValue(key, SimulatedVehicleRedisFixtures.speed(1.0f), now)
            }
            override fun diagnostics(): VehicleDataSourceDiagnostics = VehicleDataSourceDiagnostics(sourceName, true, requested.size, "fake")
        }

        val snapshot = RedisVehicleSnapshotProvider(
            dataSource = source,
            keys = listOf(VehicleRedisKeys.SPEED, VehicleRedisKeys.BATTERY, VehicleRedisKeys.SAM),
            snapshotDeadlineMs = 150L,
            clockMs = { now }
        ).readSnapshot()

        assertEquals(listOf(VehicleRedisKeys.SPEED, VehicleRedisKeys.BATTERY), source.requested)
        assertEquals("snapshot_deadline_exceeded", snapshot.keyStatuses.getValue(VehicleRedisKeys.SAM).error)
    }

}

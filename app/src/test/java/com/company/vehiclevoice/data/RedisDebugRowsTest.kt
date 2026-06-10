package com.company.vehiclevoice.data

import com.company.vehiclevoice.data.readonly.RedisDebugRows
import com.company.vehiclevoice.data.readonly.RedisVehicleSnapshotProvider
import com.company.vehiclevoice.data.readonly.VehicleRedisKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RedisDebugRowsTest {
    @Test
    fun voiceInfoRows_coverAllVoiceReadableInformationTypes() {
        val rows = RedisDebugRows.voiceInfoRows(RedisVehicleSnapshotProvider.simulated().readSnapshot())
        val labels = rows.map { it.expectedInfo }

        listOf(
            "车辆状态 / 车况总览",
            "Redis 数据健康 / Key 诊断",
            "时间戳 / 新鲜度",
            "当前车速",
            "当前档位（schema 待确认）",
            "驻车状态（schema 待确认）",
            "电量 / SOC",
            "电池详情 / 电压 / 电流",
            "剩余里程 / 续航",
            "空调状态",
            "温度信息",
            "车门状态",
            "位置 / 经纬度 / 航向 / RTK",
            "车辆姿态 / UTM / 速度分量",
            "障碍物数量 / 最近目标",
            "交通灯 / 红绿灯",
            "车道线 / LaneList",
            "规划轨迹 / planned_trajectory",
            "胎压 / 轮胎状态",
            "智能驾驶 / 自动驾驶状态",
            "ACC 状态",
            "LKA 状态",
            "不能进入自动驾驶原因",
            "退出自动驾驶原因",
            "接管提醒",
            "故障 / 告警摘要",
            "Sensor_SAM 实车状态",
            "Sensor_SAM 协作场景 / V2X 类型",
            "Sensor_SAM 协作事件",
            "Sensor_SAM 协作车数量",
            "Sensor_SAM 协作决策 / 反馈 / 行为"
        ).forEach { expected -> assertTrue("missing row $expected", labels.contains(expected)) }
    }

    @Test
    fun redisKeyRows_coverEveryDefaultReadOnlyRedisKey() {
        val rows = RedisDebugRows.redisKeyRows(RedisVehicleSnapshotProvider.simulated().readSnapshot())

        assertEquals(VehicleRedisKeys.defaultReadOnlyKeys.size, rows.size)
        assertEquals(VehicleRedisKeys.defaultReadOnlyKeys, rows.map { it.redisKeys.first() })
        assertTrue(rows.first { it.redisKeys.first() == VehicleRedisKeys.RANGE }.redisKeys.contains(VehicleRedisKeys.RANGE_LEGACY))
        assertTrue(rows.first { it.redisKeys.first() == VehicleRedisKeys.TRAFFIC_LIGHTS }.redisKeys.contains(VehicleRedisKeys.TRAFFIC_LIGHTS_LEGACY))
        assertTrue(rows.first { it.redisKeys.first() == VehicleRedisKeys.SAM }.redisKeys.contains(VehicleRedisKeys.SAM_LEGACY))
        assertTrue(rows.all { it.readableStatus.contains("已读到") })
    }
}

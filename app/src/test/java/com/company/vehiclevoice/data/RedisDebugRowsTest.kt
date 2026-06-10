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
            "当前车速",
            "当前档位",
            "电量 / SOC",
            "剩余里程 / 续航",
            "空调状态",
            "温度信息",
            "车门状态",
            "位置 / 经纬度 / 航向",
            "前方障碍物 / 最近目标",
            "交通灯 / 红绿灯",
            "胎压 / 轮胎状态",
            "智能驾驶 / 自动驾驶状态",
            "ACC 状态",
            "LKA 状态",
            "不能进入自动驾驶原因",
            "退出自动驾驶原因",
            "接管提醒",
            "故障 / 告警摘要",
            "Sam 协作场景 / V2X 类型",
            "Sam 协作事件",
            "Sam 协作车数量",
            "Sam 协作决策 / 反馈 / 行为"
        ).forEach { expected -> assertTrue("missing row $expected", labels.contains(expected)) }
    }

    @Test
    fun redisKeyRows_coverEveryDefaultReadOnlyRedisKey() {
        val rows = RedisDebugRows.redisKeyRows(RedisVehicleSnapshotProvider.simulated().readSnapshot())

        assertEquals(VehicleRedisKeys.defaultReadOnlyKeys.size, rows.size)
        assertEquals(VehicleRedisKeys.defaultReadOnlyKeys, rows.map { it.redisKeys.single() })
        assertTrue(rows.all { it.readableStatus.contains("已读到") })
    }
}

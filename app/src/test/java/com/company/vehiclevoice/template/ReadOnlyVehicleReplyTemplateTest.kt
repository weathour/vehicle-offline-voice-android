package com.company.vehiclevoice.template

import com.company.vehiclevoice.data.MockRedisStore
import com.company.vehiclevoice.data.readonly.RedisVehicleSnapshotProvider
import com.company.vehiclevoice.data.readonly.SimulatedRedisBinaryDataSource
import com.company.vehiclevoice.data.readonly.SimulatedVehicleRedisFixtures
import com.company.vehiclevoice.data.readonly.VehicleRedisKeys
import com.company.vehiclevoice.data.readonly.VehicleSnapshotStateMapper
import com.company.vehiclevoice.nlu.RuleIntentParser
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadOnlyVehicleReplyTemplateTest {
    @Test
    fun rendersReadOnlyVehicleRepliesFromSnapshotMirror() {
        val store = MockRedisStore(VehicleSnapshotStateMapper.toStateMap(RedisVehicleSnapshotProvider.simulated().readSnapshot()))
        val engine = ReplyTemplateEngine()
        val parser = RuleIntentParser()

        assertTrue(engine.render(parser.parse("速度怎样"), store).contains("12.5"))
        assertTrue(engine.render(parser.parse("速度怎样"), store).contains("Sensor_Location.linear_velocity"))
        assertTrue(engine.render(parser.parse("当前档位"), store).contains("schema 待车端确认"))
        assertTrue(engine.render(parser.parse("电量多少"), store).contains("76.0"))
        assertTrue(engine.render(parser.parse("电池详情"), store).contains("电压612.0伏"))
        assertTrue(engine.render(parser.parse("空调开了吗"), store).contains("空调开启"))
        assertTrue(engine.render(parser.parse("RTK状态"), store).contains("RTK 原始标志"))
        assertTrue(engine.render(parser.parse("车辆姿态"), store).contains("俯仰"))
        assertTrue(engine.render(parser.parse("障碍物数量"), store).contains("2个"))
        assertTrue(engine.render(parser.parse("最近障碍物"), store).contains("最近障碍物车辆"))
        assertTrue(engine.render(parser.parse("红绿灯"), store).contains("绿灯"))
        assertTrue(engine.render(parser.parse("车道线状态"), store).contains("车道2条"))
        assertTrue(engine.render(parser.parse("规划轨迹"), store).contains("3个点"))
        assertTrue(engine.render(parser.parse("胎压正常吗"), store).contains("胎压状态正常"))
        assertTrue(engine.render(parser.parse("智能驾驶状态怎么样"), store).contains("L2功能激活"))
        assertTrue(engine.render(parser.parse("ACC状态"), store).contains("ACC激活"))
        assertTrue(engine.render(parser.parse("LKA状态"), store).contains("LKA激活"))
        assertTrue(engine.render(parser.parse("当前协作场景是什么"), store).contains("V2V协作式变道"))
        assertTrue(engine.render(parser.parse("协作事件开始了吗"), store).contains("进行中"))
        assertTrue(engine.render(parser.parse("现在有几辆协作车"), store).contains("2辆"))
        assertTrue(engine.render(parser.parse("引导决策是什么"), store).contains("引导决策允许"))
        assertTrue(engine.render(parser.parse("SAM状态"), store).contains("SAM车速"))
        assertTrue(engine.render(parser.parse("Redis状态"), store).contains("已解码"))
        assertTrue(engine.render(parser.parse("数据新鲜度"), store).contains("定位时间戳"))
        assertTrue(engine.render(parser.parse("Key诊断"), store).contains("已解码"))
    }

    @Test
    fun caveatsTimestampOnlyTrafficAndLaneData() {
        val snapshot = RedisVehicleSnapshotProvider(
            SimulatedRedisBinaryDataSource(
                mapOf(
                    VehicleRedisKeys.TRAFFIC_LIGHTS to SimulatedVehicleRedisFixtures.trafficLightsTimestampOnly(),
                    VehicleRedisKeys.LANES to SimulatedVehicleRedisFixtures.laneListTimestampOnly()
                )
            ),
            keys = listOf(VehicleRedisKeys.TRAFFIC_LIGHTS, VehicleRedisKeys.LANES)
        ).readSnapshot()
        val store = MockRedisStore(VehicleSnapshotStateMapper.toStateMap(snapshot))
        val engine = ReplyTemplateEngine()
        val parser = RuleIntentParser()

        assertTrue(engine.render(parser.parse("红绿灯"), store).contains("没有颜色或相位业务数据"))
        assertTrue(engine.render(parser.parse("车道线状态"), store).contains("没有车道线业务数据"))
    }
}

package com.company.vehiclevoice.template

import com.company.vehiclevoice.data.MockRedisStore
import com.company.vehiclevoice.data.readonly.RedisVehicleSnapshotProvider
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
        assertTrue(engine.render(parser.parse("电量多少"), store).contains("76.0"))
        assertTrue(engine.render(parser.parse("空调开了吗"), store).contains("空调开启"))
        assertTrue(engine.render(parser.parse("红绿灯"), store).contains("绿灯"))
        assertTrue(engine.render(parser.parse("胎压正常吗"), store).contains("胎压状态正常"))
        assertTrue(engine.render(parser.parse("智能驾驶状态怎么样"), store).contains("L2功能激活"))
        assertTrue(engine.render(parser.parse("ACC状态"), store).contains("ACC激活"))
        assertTrue(engine.render(parser.parse("LKA状态"), store).contains("LKA激活"))
        assertTrue(engine.render(parser.parse("当前协作场景是什么"), store).contains("V2V协作式变道"))
        assertTrue(engine.render(parser.parse("协作事件开始了吗"), store).contains("进行中"))
        assertTrue(engine.render(parser.parse("现在有几辆协作车"), store).contains("2辆"))
        assertTrue(engine.render(parser.parse("引导决策是什么"), store).contains("引导决策允许"))
    }
}

package com.company.vehiclevoice.nlu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReadOnlyVehicleQueryIntentTest {
    private val parser = RuleIntentParser()

    @Test
    fun parsesReadOnlyVehicleQueriesAsNonActionable() {
        val cases = mapOf(
            "速度怎样" to "vehicle_speed_query",
            "当前档位" to "vehicle_gear_query",
            "电量多少" to "vehicle_battery_query",
            "还能跑多远" to "vehicle_range_query",
            "空调开了吗" to "vehicle_ac_query",
            "当前温度" to "vehicle_temperature_query",
            "车门关了吗" to "vehicle_door_query",
            "当前位置" to "vehicle_location_query",
            "前方有没有障碍物" to "vehicle_obstacle_query",
            "红绿灯" to "vehicle_traffic_light_query",
            "胎压正常吗" to "vehicle_tire_query",
            "智能驾驶状态怎么样" to "vehicle_intelligent_status_query",
            "ACC状态" to "vehicle_acc_query",
            "LKA状态" to "vehicle_lka_query",
            "为什么不能进入自动驾驶" to "vehicle_autod_limit_query",
            "为什么退出自动驾驶" to "vehicle_autod_out_query",
            "有没有需要接管" to "vehicle_takeover_query",
            "有故障吗" to "vehicle_fault_query",
            "当前协作场景是什么" to "vehicle_cooperation_scene_query",
            "协作事件开始了吗" to "vehicle_cooperation_event_query",
            "现在有几辆协作车" to "vehicle_cooperation_count_query",
            "引导决策是什么" to "vehicle_cooperation_decision_query",
            "检查当前车辆状态" to "status_query",
            "查看车况" to "status_query"
        )

        cases.forEach { (text, intent) ->
            val result = parser.parse(text)
            assertEquals(text, intent, result.intent.name)
            assertFalse(text, result.isActionable)
        }
    }

    @Test
    fun parsesCooperationQueriesWithCommonAsrConfusions() {
        val cases = mapOf(
            "当前写作场景是什么" to "vehicle_cooperation_scene_query",
            "写作场景是什么" to "vehicle_cooperation_scene_query",
            "当前写作常见吃什么" to "vehicle_cooperation_scene_query",
            "当前协作常见是什么" to "vehicle_cooperation_scene_query",
            "当前协作场见是什么" to "vehicle_cooperation_scene_query",
            "现在是什么协同场景" to "vehicle_cooperation_scene_query",
            "合作场景是什么" to "vehicle_cooperation_scene_query",
            "v二v还是v二i" to "vehicle_cooperation_scene_query",
            "写作事件开始了吗" to "vehicle_cooperation_event_query",
            "现在有几辆写作车" to "vehicle_cooperation_count_query",
            "写作反馈结果是什么" to "vehicle_cooperation_decision_query"
        )

        cases.forEach { (text, intent) ->
            val result = parser.parse(text)
            assertEquals(text, intent, result.intent.name)
            assertFalse(text, result.isActionable)
        }
    }

    @Test
    fun parsesVehicleQueriesWithCommonAsrConfusionsAcrossDomains() {
        val cases = mapOf(
            "当前车素多少" to "vehicle_speed_query",
            "现在单位" to "vehicle_gear_query",
            "店量多少" to "vehicle_battery_query",
            "续行多少" to "vehicle_range_query",
            "空条状态" to "vehicle_ac_query",
            "车内问度多少" to "vehicle_temperature_query",
            "车们关了吗" to "vehicle_door_query",
            "当前位子在哪" to "vehicle_location_query",
            "前方有没有张碍物" to "vehicle_obstacle_query",
            "红路灯什么颜色" to "vehicle_traffic_light_query",
            "胎呀正常吗" to "vehicle_tire_query",
            "智架状态" to "vehicle_intelligent_status_query",
            "a c c怎么样" to "vehicle_acc_query",
            "l k a状态" to "vehicle_lka_query",
            "为什么制动驾驶进不去" to "vehicle_autod_limit_query",
            "自动加时为什么退出" to "vehicle_autod_out_query",
            "需要借管吗" to "vehicle_takeover_query",
            "有没有告井" to "vehicle_fault_query",
            "写作时间开始了吗" to "vehicle_cooperation_event_query",
            "引到绝策是什么" to "vehicle_cooperation_decision_query"
        )

        cases.forEach { (text, intent) ->
            val result = parser.parse(text)
            assertEquals(text, intent, result.intent.name)
            assertFalse(text, result.isActionable)
        }
    }

    @Test
    fun cooperationAsrCorrectionDoesNotTurnUnrelatedWritingIntoVehicleIntent() {
        val result = parser.parse("写作文章")

        assertEquals("fallback", result.intent.name)
        assertFalse(result.isActionable)
    }
}

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
            "Redis状态" to "vehicle_data_health_query",
            "数据新鲜度" to "vehicle_data_freshness_query",
            "Key诊断" to "vehicle_key_diagnostic_query",
            "电池详情" to "vehicle_battery_detail_query",
            "RTK状态" to "vehicle_rtk_query",
            "车辆姿态" to "vehicle_pose_query",
            "障碍物数量" to "vehicle_obstacle_count_query",
            "最近障碍物" to "vehicle_nearest_obstacle_query",
            "规划轨迹" to "vehicle_trajectory_query",
            "车道线状态" to "vehicle_lane_query",
            "SAM状态" to "vehicle_sam_status_query",
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
            "引到绝策是什么" to "vehicle_cooperation_decision_query",
            "瑞迪斯状态" to "vehicle_data_health_query",
            "心鲜度" to "vehicle_data_freshness_query",
            "建诊断" to "vehicle_key_diagnostic_query",
            "二梯开状态" to "vehicle_rtk_query",
            "归迹点" to "vehicle_trajectory_query",
            "车到线状态" to "vehicle_lane_query",
            "山姆反馈" to "vehicle_sam_status_query"
        )

        cases.forEach { (text, intent) ->
            val result = parser.parse(text)
            assertEquals(text, intent, result.intent.name)
            assertFalse(text, result.isActionable)
        }
    }

    @Test
    fun distinctiveStandaloneSoundsTriggerReadOnlyQueries() {
        val cases = mapOf(
            "车素" to "vehicle_speed_query",
            "党" to "vehicle_gear_query",
            "店量" to "vehicle_battery_query",
            "电驰" to "vehicle_battery_detail_query",
            "序行" to "vehicle_range_query",
            "空条" to "vehicle_ac_query",
            "问度" to "vehicle_temperature_query",
            "车们" to "vehicle_door_query",
            "瑞迪丝" to "vehicle_data_health_query",
            "缓冲" to "vehicle_data_health_query",
            "时间错" to "vehicle_data_freshness_query",
            "阿踢凯" to "vehicle_rtk_query",
            "位子" to "vehicle_location_query",
            "自太" to "vehicle_pose_query",
            "张碍物" to "vehicle_obstacle_query",
            "归迹" to "vehicle_trajectory_query",
            "胎呀" to "vehicle_tire_query",
            "智架" to "vehicle_intelligent_status_query",
            "艾尔二" to "vehicle_intelligent_status_query",
            "艾斯欧西" to "vehicle_battery_query",
            "诶西西" to "vehicle_acc_query",
            "艾勒开诶" to "vehicle_lka_query",
            "车到保持" to "vehicle_lka_query",
            "借管" to "vehicle_takeover_query",
            "告井" to "vehicle_fault_query",
            "赛姆" to "vehicle_sam_status_query",
            "写作常见" to "vehicle_cooperation_scene_query",
            "微二艾克斯" to "vehicle_cooperation_scene_query",
            "写作车" to "vehicle_cooperation_count_query",
            "车况" to "status_query",
            "请帮我查一下车素" to "vehicle_speed_query",
            "报党" to "vehicle_gear_query",
            "告诉我瑞迪丝" to "vehicle_data_health_query",
            "看看写作常见" to "vehicle_cooperation_scene_query",
            "问胎呀" to "vehicle_tire_query"
        )

        cases.forEach { (text, intent) ->
            val result = parser.parse(text)
            assertEquals(text, intent, result.intent.name)
            assertEquals(text, "distinctive_alias", result.reason)
            assertFalse(text, result.isActionable)
        }
    }

    @Test
    fun genericSingleSoundsDoNotTriggerAnIntent() {
        listOf("车", "电", "门", "灯", "状态", "场景", "结果", "反馈", "可以", "打开", "不要看车速").forEach { text ->
            val result = parser.parse(text)
            assertEquals(text, "fallback", result.intent.name)
            assertFalse(text, result.isActionable)
        }
    }

    @Test
    fun unsafeTextWinsOverDistinctiveAlias() {
        val result = parser.parse("上传车速")

        assertEquals("unsafe_rejected", result.intent.name)
        assertFalse(result.isActionable)
    }

    @Test
    fun cooperationAsrCorrectionDoesNotTurnUnrelatedWritingIntoVehicleIntent() {
        val result = parser.parse("写作文章")

        assertEquals("fallback", result.intent.name)
        assertFalse(result.isActionable)
    }
}

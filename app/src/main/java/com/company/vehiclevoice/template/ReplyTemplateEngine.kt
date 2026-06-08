package com.company.vehiclevoice.template

import com.company.vehiclevoice.data.VehicleStateStore
import com.company.vehiclevoice.nlu.ParseResult

class ReplyTemplateEngine {
    fun render(parseResult: ParseResult, store: VehicleStateStore): String = when (parseResult.replyKey) {
        "air_conditioner_on" -> "已为你打开空调"
        "air_conditioner_off" -> "已为你关闭空调"
        "temperature_up" -> "已为你调高温度"
        "temperature_down" -> "已为你调低温度"
        "window_open" -> "已为你打开车窗"
        "window_close" -> "已为你关闭车窗"
        "scene_switch" -> "已为你切换场景"
        "status_query" -> "当前状态：${formatState(store.snapshot())}"
        "vehicle_speed_query" -> store.valueReply("vehicle.speed_kmh", "当前车速", "公里每小时", "暂未读取到车速")
        "vehicle_gear_query" -> store.valueReply("vehicle.gear", "当前档位", "", "暂未读取到档位")
        "vehicle_battery_query" -> store.valueReply("vehicle.battery_soc_percent", "当前电量", "%", "暂未读取到电量")
        "vehicle_range_query" -> store.valueReply("vehicle.remaining_range_km", "剩余里程", "公里", "暂未读取到剩余里程")
        "vehicle_ac_query" -> renderAc(store)
        "vehicle_temperature_query" -> renderTemperature(store)
        "vehicle_door_query" -> renderDoor(store)
        "vehicle_location_query" -> renderLocation(store)
        "vehicle_obstacle_query" -> renderObstacle(store)
        "vehicle_traffic_light_query" -> renderTrafficLight(store)
        "vehicle_tire_query" -> renderTire(store)
        "vehicle_intelligent_status_query" -> renderIntelligentStatus(store)
        "vehicle_acc_query" -> renderAcc(store)
        "vehicle_lka_query" -> renderLka(store)
        "vehicle_autod_limit_query" -> store.valueReply("vehicle.autod.limit_reason", "限制进入自动驾驶原因", "", "暂未读取到限制进入自动驾驶原因")
        "vehicle_autod_out_query" -> store.valueReply("vehicle.autod.out_reason", "退出自动驾驶原因", "", "暂未读取到退出自动驾驶原因")
        "vehicle_takeover_query" -> store.valueReply("vehicle.takeover_request", "接管提醒", "", "暂未读取到接管提醒")
        "vehicle_fault_query" -> renderFault(store)
        "vehicle_cooperation_scene_query" -> renderCooperationScene(store)
        "vehicle_cooperation_event_query" -> store.valueReply("vehicle.cooperation.event", "协作事件", "", "暂未读取到协作事件")
        "vehicle_cooperation_count_query" -> store.valueReply("vehicle.cooperation.collaborative_vehicle_count", "当前协作车数量", "辆", "暂未读取到协作车数量")
        "vehicle_cooperation_decision_query" -> renderCooperationDecision(store)
        "unsafe_rejected" -> "该指令不属于离线车控范围，已拒绝执行"
        else -> "没有识别到有效指令"
    }

    private fun VehicleStateStore.valueReply(key: String, prefix: String, suffix: String, fallback: String): String {
        val value = get(key) ?: return fallback
        return if (suffix.isBlank()) "$prefix：$value" else "$prefix：$value$suffix"
    }

    private fun renderAc(store: VehicleStateStore): String {
        val power = store.get("vehicle.ac_power") ?: return "暂未读取到空调状态"
        val mode = store.get("vehicle.ac_mode")
        val fan = store.get("vehicle.ac_fan_gear")
        return listOfNotNull("空调$power", mode?.let { "模式$it" }, fan?.let { "风机${it}档" }).joinToString("，")
    }

    private fun renderTemperature(store: VehicleStateStore): String {
        val inCar = store.get("vehicle.in_car_temp_c")
        val outCar = store.get("vehicle.out_car_temp_c")
        val set = store.get("vehicle.ac_set_temp_c")
        val parts = listOfNotNull(
            inCar?.let { "车内${it}度" },
            outCar?.let { "车外${it}度" },
            set?.let { "空调设定${it}度" }
        )
        return parts.takeIf { it.isNotEmpty() }?.joinToString("，") ?: "暂未读取到温度"
    }

    private fun renderDoor(store: VehicleStateStore): String {
        val front = store.get("vehicle.front_door")
        val mid = store.get("vehicle.mid_door")
        val parts = listOfNotNull(front?.let { "前门$it" }, mid?.let { "中门$it" })
        return parts.takeIf { it.isNotEmpty() }?.joinToString("，") ?: "暂未读取到车门状态"
    }

    private fun renderLocation(store: VehicleStateStore): String {
        val lon = store.get("vehicle.location_lon")
        val lat = store.get("vehicle.location_lat")
        val heading = store.get("vehicle.heading_deg")
        if (lon == null || lat == null) return "暂未读取到定位"
        return listOfNotNull("当前位置经度$lon，纬度$lat", heading?.let { "航向${it}度" }).joinToString("，")
    }

    private fun renderObstacle(store: VehicleStateStore): String {
        val type = store.get("vehicle.nearest_obstacle_type") ?: return "暂未检测到障碍物数据"
        val x = store.get("vehicle.nearest_obstacle_x_m")
        val y = store.get("vehicle.nearest_obstacle_y_m")
        return listOfNotNull("最近障碍物$type", x?.let { "前方${it}米" }, y?.let { "横向${it}米" }).joinToString("，")
    }

    private fun renderTrafficLight(store: VehicleStateStore): String {
        val color = store.get("vehicle.traffic_light") ?: return "暂未读取到交通灯"
        val confidence = store.get("vehicle.traffic_light_confidence")
        return if (confidence == null) "当前交通灯：$color" else "当前交通灯：$color，置信度$confidence"
    }

    private fun renderTire(store: VehicleStateStore): String {
        val summary = store.get("vehicle.tire.alarm_summary") ?: return "暂未读取到胎压状态"
        val pressure = store.get("vehicle.tire.pressure_kpa")
        val temp = store.get("vehicle.tire.temperature_c")
        val normal = store.get("vehicle.tire.normal") == "true"
        val prefix = if (normal) "胎压状态正常" else "胎压存在告警：$summary"
        return listOfNotNull(prefix, pressure?.let { "压力${it}千帕" }, temp?.let { "温度${it}度" }).joinToString("，")
    }

    private fun renderIntelligentStatus(store: VehicleStateStore): String {
        val driveMode = store.get("vehicle.drive_mode")
        val l2 = store.get("vehicle.l2.summary")
        val warning = store.get("vehicle.warning_summary")
        val parts = listOfNotNull(driveMode?.let { "驾驶模式$it" }, l2, warning?.let { "告警$it" })
        return parts.takeIf { it.isNotEmpty() }?.joinToString("，") ?: "暂未读取到智能驾驶状态"
    }

    private fun renderAcc(store: VehicleStateStore): String {
        val status = store.get("vehicle.acc.status") ?: return "暂未读取到 ACC 状态"
        val mode = store.get("vehicle.acc.mode")
        val fail = store.get("vehicle.acc.fail_reason")?.takeUnless { it == "无不满足条件" }
        val quit = store.get("vehicle.acc.quit_reason")?.takeUnless { it == "无不满足条件" }
        return listOfNotNull("ACC$status", mode?.let { "模式$it" }, fail?.let { "启动失败原因$it" }, quit?.let { "退出原因$it" }).joinToString("，")
    }

    private fun renderLka(store: VehicleStateStore): String {
        val status = store.get("vehicle.lka.status") ?: return "暂未读取到 LKA 状态"
        val fail = store.get("vehicle.lka.fail_reason")?.takeUnless { it == "无不满足条件" }
        val quit = store.get("vehicle.lka.quit_reason")?.takeUnless { it == "无不满足条件" }
        return listOfNotNull("LKA$status", fail?.let { "启动失败原因$it" }, quit?.let { "退出原因$it" }).joinToString("，")
    }

    private fun renderFault(store: VehicleStateStore): String {
        val warning = store.get("vehicle.warning_summary")
        val tire = store.get("vehicle.tire.alarm_summary")
        val perception = store.get("vehicle.perception.fault_summary")
        val parts = listOfNotNull(
            warning?.let { "车辆告警：$it" },
            tire?.let { "胎压：$it" },
            perception?.let { "感知：$it" }
        )
        return parts.takeIf { it.isNotEmpty() }?.joinToString("；") ?: "暂未读取到故障信息"
    }

    private fun renderCooperationScene(store: VehicleStateStore): String {
        val scene = store.get("vehicle.cooperation.scene") ?: return "暂未读取到协作场景"
        val type = store.get("vehicle.cooperation.v2x_type")
        return if (type == null) "当前协作场景：$scene" else "当前协作场景：$scene，类型$type"
    }

    private fun renderCooperationDecision(store: VehicleStateStore): String {
        val guide = store.get("vehicle.cooperation.guide_decision")
        val feedback = store.get("vehicle.cooperation.feedback_result")
        val behavior = store.get("vehicle.cooperation.coordinate_behavior")
        val parts = listOfNotNull(guide?.let { "引导决策$it" }, feedback?.let { "反馈$it" }, behavior?.let { "当前行为$it" })
        return parts.takeIf { it.isNotEmpty() }?.joinToString("，") ?: "暂未读取到协作决策"
    }

    private fun formatState(state: Map<String, String>): String {
        if (state.isEmpty()) return "暂无状态"
        val preferredKeys = listOf(
            "vehicle.summary.basic",
            "vehicle.summary.warning",
            "vehicle.summary.cooperation",
            "vehicle.speed_kmh",
            "vehicle.gear",
            "vehicle.battery_soc_percent",
            "vehicle.remaining_range_km",
            "vehicle.ac_power",
            "vehicle.in_car_temp_c",
            "vehicle.front_door",
            "vehicle.tire.alarm_summary",
            "vehicle.traffic_light",
            "air_conditioner",
            "window",
            "cabin_temperature_celsius"
        )
        val preferred = preferredKeys.mapNotNull { key -> state[key]?.let { key to it } }
        val entries = preferred.ifEmpty { state.entries.take(12).map { it.key to it.value } }
        return entries.joinToString("，") { "${it.first}=${it.second}" }
    }
}

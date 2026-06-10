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
        "vehicle_key_diagnostic_query" -> renderKeyDiagnostic(store)
        "vehicle_data_health_query" -> renderDataHealth(store)
        "vehicle_data_freshness_query" -> renderDataFreshness(store)
        "vehicle_speed_query" -> renderSpeed(store)
        "vehicle_gear_query" -> store.valueReply("vehicle.gear", "当前档位", "", "暂未读取到档位")
        "vehicle_battery_query" -> store.valueReply("vehicle.battery_soc_percent", "当前电量", "%", "暂未读取到电量")
        "vehicle_battery_detail_query" -> renderBatteryDetail(store)
        "vehicle_range_query" -> store.valueReply("vehicle.remaining_range_km", "剩余里程", "公里", "暂未读取到剩余里程")
        "vehicle_ac_query" -> renderAc(store)
        "vehicle_temperature_query" -> renderTemperature(store)
        "vehicle_door_query" -> renderDoor(store)
        "vehicle_rtk_query" -> renderRtk(store)
        "vehicle_pose_query" -> renderPose(store)
        "vehicle_location_query" -> renderLocation(store)
        "vehicle_obstacle_count_query" -> renderObstacleCount(store)
        "vehicle_nearest_obstacle_query" -> renderNearestObstacle(store)
        "vehicle_obstacle_query" -> renderObstacle(store)
        "vehicle_trajectory_query" -> renderTrajectory(store)
        "vehicle_lane_query" -> renderLane(store)
        "vehicle_traffic_light_query" -> renderTrafficLight(store)
        "vehicle_tire_query" -> renderTire(store)
        "vehicle_intelligent_status_query" -> renderIntelligentStatus(store)
        "vehicle_acc_query" -> renderAcc(store)
        "vehicle_lka_query" -> renderLka(store)
        "vehicle_autod_limit_query" -> store.valueReply("vehicle.autod.limit_reason", "限制进入自动驾驶原因", "", "暂未读取到限制进入自动驾驶原因")
        "vehicle_autod_out_query" -> store.valueReply("vehicle.autod.out_reason", "退出自动驾驶原因", "", "暂未读取到退出自动驾驶原因")
        "vehicle_takeover_query" -> store.valueReply("vehicle.takeover_request", "接管提醒", "", "暂未读取到接管提醒")
        "vehicle_fault_query" -> renderFault(store)
        "vehicle_sam_status_query" -> renderSamStatus(store)
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

    private fun renderDataHealth(store: VehicleStateStore): String {
        val connected = store.get("vehicle.snapshot.connected")?.toBooleanStrictOrNull()
        val decoded = store.get("vehicle.snapshot.decoded_keys")
        val missing = store.get("vehicle.snapshot.missing_keys")
        val errors = store.get("vehicle.snapshot.decode_errors")
        val timestampOnly = store.get("vehicle.snapshot.timestamp_only_keys").orEmpty().takeIf { it.isNotBlank() }
        val connectedText = when (connected) {
            true -> "Redis 数据源已连接"
            false -> "Redis 数据源未连接"
            null -> "暂未读取到 Redis 连接状态"
        }
        return listOfNotNull(
            connectedText,
            decoded?.let { "已解码${it}个键" },
            missing?.let { "缺失${it}个键" },
            errors?.let { "解码错误${it}个" },
            timestampOnly?.let { "仅时间戳：$it" }
        ).joinToString("，")
    }

    private fun renderDataFreshness(store: VehicleStateStore): String {
        val timestamps = listOfNotNull(
            store.get("vehicle.location_timestamp")?.let { "定位时间戳$it" },
            store.get("vehicle.traffic_light_timestamp")?.let { "交通灯时间戳$it" },
            store.get("vehicle.lane_timestamp")?.let { "车道线时间戳$it" }
        )
        val timestampOnly = store.get("vehicle.snapshot.timestamp_only_keys").orEmpty().takeIf { it.isNotBlank() }
        val parts = timestamps + listOfNotNull(timestampOnly?.let { "仅有时间戳、无业务数据的键：$it" })
        return parts.takeIf { it.isNotEmpty() }?.joinToString("，")
            ?: "暂未读取到可用于判断新鲜度的时间戳"
    }

    private fun renderKeyDiagnostic(store: VehicleStateStore): String {
        val decoded = store.get("vehicle.snapshot.decoded_keys")
        val missing = store.get("vehicle.snapshot.missing_keys")
        val errors = store.get("vehicle.snapshot.decode_errors")
        val timestampOnly = store.get("vehicle.snapshot.timestamp_only_keys").orEmpty().takeIf { it.isNotBlank() }
        val errorEntries = store.snapshot()
            .filterKeys { it.startsWith("vehicle.snapshot.error_") }
            .toSortedMap()
            .values
            .take(3)
        val parts = listOfNotNull(
            decoded?.let { "已解码${it}个键" },
            missing?.let { "缺失${it}个键" },
            errors?.let { "解码错误${it}个" },
            timestampOnly?.let { "仅时间戳：$it" }
        ) + errorEntries.map { "异常$it" }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("，") ?: "暂未读取到 Redis 键诊断信息"
    }

    private fun renderSpeed(store: VehicleStateStore): String {
        val speed = store.get("vehicle.speed_kmh") ?: return "暂未读取到车速"
        val source = store.get("vehicle.speed_source")
        return listOfNotNull("当前车速：${speed}公里每小时", source?.let { "来源$it" }).joinToString("，")
    }

    private fun renderBatteryDetail(store: VehicleStateStore): String {
        val soc = store.get("vehicle.battery_soc_percent")
        val voltage = store.get("vehicle.battery_voltage_v")
        val current = store.get("vehicle.battery_current_a")
        val parts = listOfNotNull(
            soc?.let { "SOC${it}%" },
            voltage?.let { "电压${it}伏" },
            current?.let { "电流${it}安" }
        )
        return parts.takeIf { it.isNotEmpty() }?.joinToString("，") ?: "暂未读取到电池详情"
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

    private fun renderRtk(store: VehicleStateStore): String {
        val flag = store.get("vehicle.rtk_flag") ?: return "暂未读取到 RTK 标志"
        return "RTK 原始标志：$flag，需按车端定义解释定位质量"
    }

    private fun renderPose(store: VehicleStateStore): String {
        val height = store.get("vehicle.location_height_m")
        val pitch = store.get("vehicle.location_pitch")
        val roll = store.get("vehicle.location_roll")
        val heading = store.get("vehicle.heading_deg")
        val parts = listOfNotNull(
            height?.let { "高度${it}米" },
            pitch?.let { "俯仰$it" },
            roll?.let { "横滚$it" },
            heading?.let { "航向${it}度" }
        )
        return parts.takeIf { it.isNotEmpty() }?.joinToString("，") ?: "暂未读取到姿态数据"
    }

    private fun renderLocation(store: VehicleStateStore): String {
        val lon = store.get("vehicle.location_lon")
        val lat = store.get("vehicle.location_lat")
        val heading = store.get("vehicle.heading_deg")
        if (lon == null || lat == null) return "暂未读取到定位"
        return listOfNotNull("当前位置经度$lon，纬度$lat", heading?.let { "航向${it}度" }).joinToString("，")
    }

    private fun renderObstacleCount(store: VehicleStateStore): String {
        val count = store.get("vehicle.obstacle_count") ?: return "暂未读取到障碍物数量"
        val nearest = store.get("vehicle.nearest_obstacle_distance_m")
        return listOfNotNull("当前障碍物数量：${count}个", nearest?.let { "最近约${it}米" }).joinToString("，")
    }

    private fun renderNearestObstacle(store: VehicleStateStore): String {
        val type = store.get("vehicle.nearest_obstacle_type")
        if (type == null) {
            return if (store.get("vehicle.obstacle_count") == "0") "当前未上报障碍物" else "暂未读取到最近障碍物数据"
        }
        val id = store.get("vehicle.nearest_obstacle_id")
        val distance = store.get("vehicle.nearest_obstacle_distance_m")
        val x = store.get("vehicle.nearest_obstacle_x_m")
        val y = store.get("vehicle.nearest_obstacle_y_m")
        val velocity = store.get("vehicle.nearest_obstacle_velocity_mps")
        val confidence = store.get("vehicle.nearest_obstacle_confidence")
        return listOfNotNull(
            "最近障碍物$type",
            id?.let { "编号$it" },
            distance?.let { "距离约${it}米" },
            x?.let { "纵向${it}米" },
            y?.let { "横向${it}米" },
            velocity?.let { "速度${it}米每秒" },
            confidence?.let { "置信度$it" }
        ).joinToString("，")
    }

    private fun renderObstacle(store: VehicleStateStore): String {
        val count = store.get("vehicle.obstacle_count")
        val nearest = renderNearestObstacle(store)
        return if (count == null) nearest else "当前障碍物${count}个，$nearest"
    }

    private fun renderTrajectory(store: VehicleStateStore): String {
        val count = store.get("vehicle.trajectory.point_count") ?: return "暂未读取到规划轨迹"
        val length = store.get("vehicle.trajectory.length_m")
        val first = store.get("vehicle.trajectory.first_point")
        val last = store.get("vehicle.trajectory.last_point")
        return listOfNotNull(
            "规划轨迹${count}个点",
            length?.let { "近似长度${it}米" },
            first?.let { "起点$it" },
            last?.let { "终点$it" }
        ).joinToString("，")
    }

    private fun renderLane(store: VehicleStateStore): String {
        if (store.get("vehicle.lane.business_data") == "false") {
            val timestamp = store.get("vehicle.lane_timestamp")
            return listOfNotNull(
                "车道线键已读取到时间戳",
                timestamp,
                "但本轮没有车道线业务数据，不能判断车道线状态"
            ).joinToString("，")
        }
        val laneCount = store.get("vehicle.lane_count")
        val lineCount = store.get("vehicle.lane_line_count")
        val confidence = store.get("vehicle.lane_confidence")
        val parts = listOfNotNull(
            laneCount?.let { "车道${it}条" },
            lineCount?.let { "车道线${it}条" },
            confidence?.let { "最高置信度$it" }
        )
        return parts.takeIf { it.isNotEmpty() }?.joinToString("，") ?: "暂未读取到车道线业务数据"
    }

    private fun renderTrafficLight(store: VehicleStateStore): String {
        if (store.get("vehicle.traffic_light.business_data") == "false") {
            val timestamp = store.get("vehicle.traffic_light_timestamp")
            return listOfNotNull(
                "交通灯键已读取到时间戳",
                timestamp,
                "但本轮没有颜色或相位业务数据，不能判断红绿灯"
            ).joinToString("，")
        }
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

    private fun renderSamStatus(store: VehicleStateStore): String {
        val scene = store.get("vehicle.cooperation.scene")
        val event = store.get("vehicle.cooperation.event")
        val autoLevel = store.get("vehicle.cooperation.auto_level")
        val driveMode = store.get("vehicle.cooperation.driving_mode_fd")
        val gear = store.get("vehicle.cooperation.gear_location_fd")
        val steering = store.get("vehicle.cooperation.steering_value_fd")
        val speed = store.get("vehicle.cooperation.speed_mps")
        val parts = listOfNotNull(
            scene?.let { "场景$it" },
            event?.let { "事件$it" },
            autoLevel?.let { "自动等级$it" },
            driveMode?.let { "驾驶模式反馈$it" },
            gear?.let { "档位反馈$it" },
            steering?.let { "转向反馈$it" },
            speed?.let { "SAM车速${it}米每秒" }
        )
        val caveat = if (scene == null) "本轮未上报协作场景 ID，不能判断 V2V/V2I 场景" else null
        return (parts + listOfNotNull(caveat)).takeIf { it.isNotEmpty() }?.joinToString("，") ?: "暂未读取到 SAM 状态"
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
            "vehicle.speed_source",
            "vehicle.gear",
            "vehicle.battery_soc_percent",
            "vehicle.battery_voltage_v",
            "vehicle.battery_current_a",
            "vehicle.remaining_range_km",
            "vehicle.obstacle_count",
            "vehicle.trajectory.point_count",
            "vehicle.ac_power",
            "vehicle.in_car_temp_c",
            "vehicle.front_door",
            "vehicle.tire.alarm_summary",
            "vehicle.traffic_light",
            "vehicle.traffic_light.business_data",
            "vehicle.lane.business_data",
            "vehicle.snapshot.timestamp_only_keys",
            "air_conditioner",
            "window",
            "cabin_temperature_celsius"
        )
        val preferred = preferredKeys.mapNotNull { key -> state[key]?.let { key to it } }
        val entries = preferred.ifEmpty { state.entries.take(12).map { it.key to it.value } }
        return entries.joinToString("，") { "${it.first}=${it.second}" }
    }
}

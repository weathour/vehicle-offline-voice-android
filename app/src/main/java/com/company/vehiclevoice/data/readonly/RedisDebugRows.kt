package com.company.vehiclevoice.data.readonly

data class RedisDebugRow(
    val expectedInfo: String,
    val redisKeys: List<String>,
    val readableStatus: String,
    val readableContent: String
) {
    val keyText: String get() = redisKeys.joinToString(" + ")
}

object RedisDebugRows {
    fun voiceInfoRows(snapshot: VehicleReadOnlySnapshot): List<RedisDebugRow> {
        val state = VehicleSnapshotStateMapper.toStateMap(snapshot)
        fun v(key: String): String? = state[key]
        fun row(label: String, keys: List<String>, content: String): RedisDebugRow = RedisDebugRow(
            expectedInfo = label,
            redisKeys = keys,
            readableStatus = statusFor(snapshot, keys),
            readableContent = content.ifBlank { "暂无可读内容" }
        )

        return listOf(
            row(
                "车辆状态 / 车况总览",
                VehicleRedisKeys.defaultReadOnlyKeys,
                listOfNotNull(v("vehicle.summary.basic"), v("vehicle.summary.warning"), v("vehicle.summary.cooperation"))
                    .joinToString("；")
            ),
            row(
                "Redis 数据健康 / Key 诊断",
                VehicleRedisKeys.defaultReadOnlyKeys,
                listOfNotNull(
                    v("vehicle.snapshot.connected")?.let { "connected=$it" },
                    v("vehicle.snapshot.decoded_keys")?.let { "decoded=$it" },
                    v("vehicle.snapshot.missing_keys")?.let { "missing=$it" },
                    v("vehicle.snapshot.decode_errors")?.let { "decodeError=$it" },
                    v("vehicle.snapshot.timestamp_only_keys")?.takeIf { it.isNotBlank() }?.let { "timestampOnly=$it" }
                ).joinToString("，")
            ),
            row(
                "时间戳 / 新鲜度",
                listOf(VehicleRedisKeys.LOCATION, VehicleRedisKeys.TRAFFIC_LIGHTS, VehicleRedisKeys.LANES),
                listOfNotNull(
                    v("vehicle.location_timestamp")?.let { "定位$it" },
                    v("vehicle.traffic_light_timestamp")?.let { "交通灯$it" },
                    v("vehicle.lane_timestamp")?.let { "车道线$it" },
                    v("vehicle.snapshot.timestamp_only_keys")?.takeIf { it.isNotBlank() }?.let { "仅时间戳$it" }
                ).joinToString("，")
            ),
            row(
                "当前车速",
                listOf(VehicleRedisKeys.SPEED, VehicleRedisKeys.LOCATION),
                listOfNotNull(
                    v("vehicle.speed_kmh")?.let { "$it km/h" },
                    v("vehicle.speed_source")?.let { "来源$it" },
                    v("vehicle.linear_velocity_mps")?.let { "线速度${it}m/s" }
                ).joinToString("，")
            ),
            row("当前档位（schema 待确认）", listOf(VehicleRedisKeys.DCU_INFO_1), "DCU_INFO_1 实车字段含 timestamp，旧 gear 映射暂不作稳定结论"),
            row("驻车状态（schema 待确认）", listOf(VehicleRedisKeys.DCU_INFO_1), "DCU_INFO_1 field 3 已观测为 shifted/changed status，暂不可信为 parking"),
            row("电量 / SOC", listOf(VehicleRedisKeys.BATTERY), v("vehicle.battery_soc_percent")?.let { "$it%" }.orEmpty()),
            row(
                "电池详情 / 电压 / 电流",
                listOf(VehicleRedisKeys.BATTERY),
                listOfNotNull(
                    v("vehicle.battery_soc_percent")?.let { "SOC=${it}%" },
                    v("vehicle.battery_voltage_v")?.let { "电压${it}V" },
                    v("vehicle.battery_current_a")?.let { "电流${it}A" }
                ).joinToString("，")
            ),
            row("剩余里程 / 续航", listOf(VehicleRedisKeys.RANGE), v("vehicle.remaining_range_km")?.let { "$it km" }.orEmpty()),
            row(
                "空调状态",
                listOf(VehicleRedisKeys.AC_STATE),
                listOfNotNull(
                    v("vehicle.ac_power")?.let { "电源$it" },
                    v("vehicle.ac_mode")?.let { "模式$it" },
                    v("vehicle.ac_fan_gear")?.let { "风机${it}档" },
                    v("vehicle.ac_set_temp_c")?.let { "设定${it}℃" }
                ).joinToString("，")
            ),
            row(
                "温度信息",
                listOf(VehicleRedisKeys.AC_TEMPERATURE, VehicleRedisKeys.AC_STATE),
                listOfNotNull(
                    v("vehicle.in_car_temp_c")?.let { "车内${it}℃" },
                    v("vehicle.out_car_temp_c")?.let { "车外${it}℃" },
                    v("vehicle.ac_set_temp_c")?.let { "空调设定${it}℃" }
                ).joinToString("，")
            ),
            row(
                "车门状态",
                listOf(VehicleRedisKeys.BODY_STATE),
                listOfNotNull(
                    v("vehicle.front_door")?.let { "前门$it" },
                    v("vehicle.mid_door")?.let { "中门$it" }
                ).joinToString("，")
            ),
            row(
                "车身扩展状态 / 喇叭 / 雨刮",
                listOf(VehicleRedisKeys.BODY_STATE),
                listOfNotNull(
                    v("vehicle.horn")?.let { "喇叭$it" },
                    v("vehicle.wiper")?.let { "雨刮$it" }
                ).joinToString("，")
            ),
            row(
                "位置 / 经纬度 / 航向 / RTK",
                listOf(VehicleRedisKeys.LOCATION),
                listOfNotNull(
                    v("vehicle.location_lon")?.let { "经度$it" },
                    v("vehicle.location_lat")?.let { "纬度$it" },
                    v("vehicle.heading_deg")?.let { "航向${it}°" },
                    v("vehicle.location_height_m")?.let { "高度${it}m" },
                    v("vehicle.rtk_flag")?.let { "rtkflag=$it" }
                ).joinToString("，")
            ),
            row(
                "车辆姿态 / UTM / 速度分量",
                listOf(VehicleRedisKeys.LOCATION),
                listOfNotNull(
                    v("vehicle.location_pitch")?.let { "pitch=$it" },
                    v("vehicle.location_roll")?.let { "roll=$it" },
                    v("vehicle.velocity_x_mps")?.let { "vx=${it}m/s" },
                    v("vehicle.velocity_y_mps")?.let { "vy=${it}m/s" },
                    v("vehicle.velocity_z_mps")?.let { "vz=${it}m/s" },
                    v("vehicle.linear_acceleration_mps2")?.let { "a=${it}m/s²" },
                    v("vehicle.angular_velocity_radps")?.let { "角速度$it" },
                    v("vehicle.utm_x")?.let { "utmX=$it" },
                    v("vehicle.utm_y")?.let { "utmY=$it" }
                ).joinToString("，")
            ),
            row(
                "障碍物数量 / 最近目标",
                listOf(VehicleRedisKeys.OBSTACLES, VehicleRedisKeys.MAIN_OBSTACLE),
                listOfNotNull(
                    v("vehicle.obstacle_count")?.let { "数量$it" },
                    v("vehicle.nearest_obstacle_type")?.let { "类型$it" },
                    v("vehicle.nearest_obstacle_id")?.let { "id=$it" },
                    v("vehicle.nearest_obstacle_x_m")?.let { "前向${it}m" },
                    v("vehicle.nearest_obstacle_y_m")?.let { "横向${it}m" },
                    v("vehicle.nearest_obstacle_z_m")?.let { "高度${it}m" },
                    v("vehicle.nearest_obstacle_distance_m")?.let { "距离${it}m" },
                    v("vehicle.nearest_obstacle_velocity_mps")?.let { "速度${it}m/s" },
                    v("vehicle.nearest_obstacle_length_m")?.let { "长${it}m" },
                    v("vehicle.nearest_obstacle_width_m")?.let { "宽${it}m" },
                    v("vehicle.nearest_obstacle_height_m")?.let { "高${it}m" },
                    v("vehicle.nearest_obstacle_confidence")?.let { "置信度$it" }
                ).joinToString("，")
            ),
            row(
                "交通灯 / 红绿灯",
                listOf(VehicleRedisKeys.TRAFFIC_LIGHTS),
                listOfNotNull(
                    v("vehicle.traffic_light_timestamp")?.let { "时间戳$it" },
                    v("vehicle.traffic_light.business_data")?.let { "业务数据=$it" },
                    v("vehicle.traffic_light")?.let { "颜色$it" },
                    v("vehicle.traffic_light_confidence")?.let { "置信度$it" },
                    v("vehicle.traffic_light_count")?.let { "数量$it" },
                    v("vehicle.traffic_light_intersection_id")?.let { "路口$it" },
                    v("vehicle.traffic_light_phase_id")?.let { "相位$it" },
                    v("vehicle.traffic_light_remaining_s")?.let { "剩余${it}s" }
                ).joinToString("，")
            ),
            row(
                "车道线 / LaneList",
                listOf(VehicleRedisKeys.LANES),
                listOfNotNull(
                    v("vehicle.lane_timestamp")?.let { "时间戳$it" },
                    v("vehicle.lane.business_data")?.let { "业务数据=$it" },
                    v("vehicle.lane_count")?.let { "车道$it" },
                    v("vehicle.lane_line_count")?.let { "线条$it" },
                    v("vehicle.lane_confidence")?.let { "置信度$it" }
                ).joinToString("，")
            ),
            row(
                "规划轨迹 / planned_trajectory",
                listOf(VehicleRedisKeys.PLANNED_TRAJECTORY),
                listOfNotNull(
                    v("vehicle.trajectory.point_count")?.let { "点数$it" },
                    v("vehicle.trajectory.length_m")?.let { "长度${it}m" },
                    v("vehicle.trajectory.first_point")?.let { "起点$it" },
                    v("vehicle.trajectory.last_point")?.let { "终点$it" }
                ).joinToString("，")
            ),
            row(
                "胎压 / 轮胎状态",
                listOf(VehicleRedisKeys.TPMS),
                listOfNotNull(
                    v("vehicle.tire.normal")?.let { "正常=$it" },
                    v("vehicle.tire.alarm_summary")?.let { "告警$it" },
                    v("vehicle.tire.pressure_kpa")?.let { "压力${it}kPa" },
                    v("vehicle.tire.temperature_c")?.let { "温度${it}℃" }
                ).joinToString("，")
            ),
            row(
                "智能驾驶 / 自动驾驶状态",
                listOf(VehicleRedisKeys.DCU_INFO_2, VehicleRedisKeys.L2_STATE),
                listOfNotNull(v("vehicle.drive_mode")?.let { "驾驶模式$it" }, v("vehicle.l2.summary"))
                    .joinToString("，")
            ),
            row(
                "ACC 状态",
                listOf(VehicleRedisKeys.L2_STATE),
                listOfNotNull(
                    v("vehicle.acc.status")?.let { "ACC$it" },
                    v("vehicle.acc.mode")?.let { "模式$it" },
                    v("vehicle.acc.fail_reason")?.let { "启动失败$it" },
                    v("vehicle.acc.quit_reason")?.let { "退出原因$it" }
                ).joinToString("，")
            ),
            row(
                "LKA 状态",
                listOf(VehicleRedisKeys.L2_STATE),
                listOfNotNull(
                    v("vehicle.lka.status")?.let { "LKA$it" },
                    v("vehicle.lka.fail_reason")?.let { "启动失败$it" },
                    v("vehicle.lka.quit_reason")?.let { "退出原因$it" }
                ).joinToString("，")
            ),
            row("不能进入自动驾驶原因", listOf(VehicleRedisKeys.DCU_INFO_2), v("vehicle.autod.limit_reason").orEmpty()),
            row("退出自动驾驶原因", listOf(VehicleRedisKeys.DCU_INFO_2), v("vehicle.autod.out_reason").orEmpty()),
            row("接管提醒", listOf(VehicleRedisKeys.DCU_INFO_2), v("vehicle.takeover_request").orEmpty()),
            row(
                "故障 / 告警摘要",
                listOf(VehicleRedisKeys.DCU_INFO_2, VehicleRedisKeys.TPMS, VehicleRedisKeys.MAIN_OBSTACLE),
                listOfNotNull(v("vehicle.warning_summary"), v("vehicle.tire.alarm_summary")?.let { "胎压$it" }, v("vehicle.perception.fault_summary")?.let { "感知$it" })
                    .joinToString("；")
            ),
            row(
                "感知故障详情",
                listOf(VehicleRedisKeys.MAIN_OBSTACLE),
                listOfNotNull(
                    v("vehicle.perception.camera_fault")?.let { "相机$it" },
                    v("vehicle.perception.radar_fault")?.let { "雷达$it" },
                    v("vehicle.perception.vehicle_connect_fault")?.let { "车联$it" },
                    v("vehicle.perception.fusion_fault")?.let { "融合$it" }
                ).joinToString("，")
            ),
            row(
                "Sensor_SAM 实车状态",
                listOf(VehicleRedisKeys.SAM),
                listOfNotNull(
                    v("vehicle.cooperation.auto_level")?.let { "autoLevel=$it" },
                    v("vehicle.cooperation.driving_mode_fd")?.let { "drivingModeFd=$it" },
                    v("vehicle.cooperation.gear_location_fd")?.let { "gearFd=$it" },
                    v("vehicle.cooperation.steering_value_fd")?.let { "steering=$it" },
                    v("vehicle.cooperation.acceleration_cmd")?.let { "accCmd=$it" },
                    v("vehicle.cooperation.speed_mps")?.let { "speed=${it}m/s" }
                ).joinToString("，")
            ),
            row(
                "Sensor_SAM 协作场景 / V2X 类型",
                listOf(VehicleRedisKeys.SAM),
                listOfNotNull(v("vehicle.cooperation.scene"), v("vehicle.cooperation.v2x_type")?.let { "类型$it" }, v("vehicle.cooperation.scene_id")?.let { "sceneId=$it" })
                    .joinToString("，")
            ),
            row("Sensor_SAM 协作事件", listOf(VehicleRedisKeys.SAM), v("vehicle.cooperation.event").orEmpty()),
            row("Sensor_SAM 协作车数量", listOf(VehicleRedisKeys.SAM), v("vehicle.cooperation.collaborative_vehicle_count")?.let { "${it}辆" }.orEmpty()),
            row(
                "Sensor_SAM 协作决策 / 反馈 / 行为",
                listOf(VehicleRedisKeys.SAM),
                listOfNotNull(
                    v("vehicle.cooperation.guide_decision")?.let { "引导决策$it" },
                    v("vehicle.cooperation.feedback_result")?.let { "反馈$it" },
                    v("vehicle.cooperation.coordinate_behavior")?.let { "当前行为$it" },
                    v("vehicle.cooperation.driving_intention")?.let { "驾驶意图$it" },
                    v("vehicle.cooperation.intent_reason")?.let { "原因$it" }
                ).joinToString("，")
            )
        )
    }

    fun redisKeyRows(snapshot: VehicleReadOnlySnapshot): List<RedisDebugRow> = VehicleRedisKeys.defaultReadOnlyKeys.map { key ->
        val status = snapshot.keyStatuses[key]
        val aliases = VehicleRedisKeys.aliases[key].orEmpty()
        RedisDebugRow(
            expectedInfo = redisKeyLabel(key),
            redisKeys = listOf(key) + aliases,
            readableStatus = when {
                status == null -> "未读取"
                status.decoded -> "已读到 / 已解码"
                status.present -> "已读到 / 解码失败"
                else -> "未读到"
            },
            readableContent = listOfNotNull(
                aliases.takeIf { it.isNotEmpty() }?.joinToString(prefix = "alias=", separator = "|"),
                status?.updatedAtMs?.let { "updatedAt=$it" },
                status?.error?.let { "error=$it" }
            ).joinToString("，").ifBlank { "key 正常" }
        )
    }

    fun summary(snapshot: VehicleReadOnlySnapshot): String {
        val decoded = snapshot.keyStatuses.values.count { it.decoded }
        val missing = snapshot.keyStatuses.values.count { !it.present }
        val errors = snapshot.keyStatuses.values.count { it.present && !it.decoded }
        val timestampOnly = timestampOnlyKeyList(snapshot)
        return listOfNotNull(
            "connected=${snapshot.diagnostics.connected}",
            "decoded=$decoded/${VehicleRedisKeys.defaultReadOnlyKeys.size}",
            "missing=$missing",
            "decodeError=$errors",
            timestampOnly.takeIf { it.isNotEmpty() }?.joinToString(prefix = "timestampOnly=", separator = "|")
        ).joinToString("，")
    }

    private fun statusFor(snapshot: VehicleReadOnlySnapshot, keys: List<String>): String {
        val statuses = keys.mapNotNull { snapshot.keyStatuses[it] }
        if (statuses.isEmpty()) return "未读取"
        val decoded = statuses.count { it.decoded }
        val present = statuses.count { it.present }
        val failed = statuses.count { it.present && !it.decoded }
        val missing = keys.size - present
        return when {
            decoded == keys.size -> "全部可读（$decoded/${keys.size}）"
            decoded > 0 -> "部分可读（decoded=$decoded/${keys.size}，missing=$missing，decodeError=$failed）"
            failed > 0 -> "解码失败（decodeError=$failed，missing=$missing）"
            missing > 0 -> "缺失（missing=$missing/${keys.size}）"
            else -> "不可读"
        }
    }

    private fun redisKeyLabel(key: String): String = when (key) {
        VehicleRedisKeys.SPEED -> "Redis key：车速"
        VehicleRedisKeys.DCU_INFO_1 -> "Redis key：档位 / 驻车"
        VehicleRedisKeys.DCU_INFO_2 -> "Redis key：自动驾驶告警 / 接管"
        VehicleRedisKeys.BATTERY -> "Redis key：电量"
        VehicleRedisKeys.RANGE -> "Redis key：剩余里程"
        VehicleRedisKeys.L2_STATE -> "Redis key：ACC / LKA / L2"
        VehicleRedisKeys.AC_TEMPERATURE -> "Redis key：车内外温度"
        VehicleRedisKeys.AC_STATE -> "Redis key：空调状态"
        VehicleRedisKeys.BODY_STATE -> "Redis key：车门 / 车身"
        VehicleRedisKeys.TPMS -> "Redis key：胎压"
        VehicleRedisKeys.LOCATION -> "Redis key：位置 / 航向 / RTK"
        VehicleRedisKeys.OBSTACLES -> "Redis key：障碍物列表"
        VehicleRedisKeys.TRAFFIC_LIGHTS -> "Redis key：交通灯"
        VehicleRedisKeys.LANES -> "Redis key：车道线列表"
        VehicleRedisKeys.MAIN_OBSTACLE -> "Redis key：主障碍物 / 感知故障"
        VehicleRedisKeys.PLANNED_TRAJECTORY -> "Redis key：规划轨迹"
        VehicleRedisKeys.SAM -> "Redis key：Sensor_SAM 协作信息"
        else -> "Redis key：$key"
    }

    private fun timestampOnlyKeyList(snapshot: VehicleReadOnlySnapshot): List<String> = listOfNotNull(
        snapshot.keyStatuses[VehicleRedisKeys.SPEED]
            ?.takeIf { it.present && !it.decoded && it.error?.contains("timestamp_only") == true }
            ?.let { VehicleRedisKeys.SPEED },
        snapshot.trafficLight?.takeIf { !it.hasBusinessData && it.timestamp != null }?.let { VehicleRedisKeys.TRAFFIC_LIGHTS },
        snapshot.laneStatus?.takeIf { !it.hasBusinessData && it.timestamp != null }?.let { VehicleRedisKeys.LANES }
    )
}

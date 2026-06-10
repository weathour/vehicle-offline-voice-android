package com.company.vehiclevoice.nlu

object AskableVoiceContent {
    data class Question(
        val phrase: String,
        val intent: String,
        val answerScope: String,
        val caveat: String? = null
    )

    data class Category(
        val title: String,
        val description: String,
        val questions: List<Question>
    )

    val categories: List<Category> = listOf(
        Category(
            title = "基础车况",
            description = "优先展示实车 Redis 已稳定解码的只读车况。",
            questions = listOf(
                Question("当前车速多少", "vehicle_speed_query", "车速，优先来自 Sensor_Location.linear_velocity"),
                Question("当前档位", "vehicle_gear_query", "档位与驻车状态"),
                Question("电量多少", "vehicle_battery_query", "SOC 百分比"),
                Question("电池详情", "vehicle_battery_detail_query", "SOC、电压、电流"),
                Question("还能跑多远", "vehicle_range_query", "DCU_INFO_St 剩余续航"),
                Question("空调开了吗", "vehicle_ac_query", "空调开关、模式、风量"),
                Question("当前温度", "vehicle_temperature_query", "车内、车外、空调设定温度"),
                Question("车门关了吗", "vehicle_door_query", "前门、中门状态"),
                Question("胎压正常吗", "vehicle_tire_query", "胎压、胎温、报警摘要")
            )
        ),
        Category(
            title = "定位、感知与轨迹",
            description = "回答位置、RTK、姿态、障碍物、车道线、交通灯和规划轨迹。",
            questions = listOf(
                Question("当前位置", "vehicle_location_query", "经纬度与航向"),
                Question("车辆姿态", "vehicle_pose_query", "高度、俯仰、横滚、航向角"),
                Question("RTK 状态", "vehicle_rtk_query", "Sensor_Location.rtkflag 原始值", "按车端定义解释定位质量"),
                Question("障碍物数量", "vehicle_obstacle_count_query", "Sensor_Mmobstacles 上报数量"),
                Question("最近障碍物", "vehicle_nearest_obstacle_query", "最近目标类型、距离、速度、置信度"),
                Question("前方有没有障碍物", "vehicle_obstacle_query", "数量加最近目标摘要"),
                Question("红绿灯", "vehicle_traffic_light_query", "颜色、置信度、相位", "若本轮只有时间戳，会明确说业务数据不可用"),
                Question("车道线状态", "vehicle_lane_query", "车道线数量、置信度", "若本轮只有时间戳，会明确说业务数据不可用"),
                Question("规划轨迹", "vehicle_trajectory_query", "轨迹点数、起终点、近似长度")
            )
        ),
        Category(
            title = "智驾、告警与接管",
            description = "解释 L2、ACC、LKA、自动驾驶进入/退出原因和故障告警。",
            questions = listOf(
                Question("智能驾驶状态", "vehicle_intelligent_status_query", "驾驶模式、L2 摘要、告警"),
                Question("ACC 状态", "vehicle_acc_query", "ACC 状态、模式、失败/退出原因"),
                Question("LKA 状态", "vehicle_lka_query", "LKA 状态、失败/退出原因"),
                Question("为什么不能进入自动驾驶", "vehicle_autod_limit_query", "限制进入自动驾驶原因"),
                Question("为什么退出自动驾驶", "vehicle_autod_out_query", "退出自动驾驶原因"),
                Question("需要接管吗", "vehicle_takeover_query", "接管提醒"),
                Question("有没有故障", "vehicle_fault_query", "车辆、胎压、感知告警摘要")
            )
        ),
        Category(
            title = "SAM 与协作",
            description = "读取 Sensor_SAM 和已有协作问答，避免把缺失字段说成真实场景。",
            questions = listOf(
                Question("SAM 状态", "vehicle_sam_status_query", "自动等级、驾驶模式反馈、档位、转向、SAM 车速", "未上报 scene_id 时不判断 V2V/V2I"),
                Question("当前协作场景是什么", "vehicle_cooperation_scene_query", "协作场景与 V2X 类型"),
                Question("协作事件开始了吗", "vehicle_cooperation_event_query", "协作事件状态"),
                Question("有几辆协作车", "vehicle_cooperation_count_query", "协作车辆数量"),
                Question("引导决策是什么", "vehicle_cooperation_decision_query", "引导决策、反馈结果、协作行为")
            )
        ),
        Category(
            title = "Redis 数据诊断",
            description = "现场调试用，只读查看连接、解码、缺失 key 和新鲜度。",
            questions = listOf(
                Question("Redis 状态", "vehicle_data_health_query", "连接状态、解码数量、缺失与错误数量"),
                Question("数据新鲜度", "vehicle_data_freshness_query", "定位、交通灯、车道线时间戳"),
                Question("Key 诊断", "vehicle_key_diagnostic_query", "缺失 key、解码错误、仅时间戳 key")
            )
        ),
        Category(
            title = "本轮不启用",
            description = "实车阶段保持只读，不在主流程引导写车控。",
            questions = listOf(
                Question("打开空调等控制写入", "deferred_vehicle_write", "不作为实车语音问答入口", "避免误写真实车辆状态"),
                Question("写 Redis 或改车辆状态", "rejected_external_write", "不支持", "只做读取、解释和诊断"),
                Question("缺字段时判断真实 V2I/V2V 场景", "deferred_v2x_claim", "不支持", "SAM 未上报 scene_id 时只说明证据不足")
            )
        )
    )

    val supportedQuestions: List<Question> = categories
        .filterNot { it.title == "本轮不启用" }
        .flatMap { it.questions }

    val visiblePhraseList: List<String> = categories.flatMap { category ->
        category.questions.map { question -> question.phrase }
    }
}

package com.company.vehiclevoice.nlu

class RuleIntentParser : IntentParser {
    override fun parse(text: String): ParseResult {
        val normalized = normalize(text)
        if (normalized.isBlank()) return fallback("empty_asr")
        if (containsUnsafeText(normalized)) {
            return ParseResult(VoiceIntent.Unsafe, replyKey = "unsafe_rejected", confidence = 1.0, reason = "unsafe_text")
        }

        return when {
            matchesAny(normalized, "打开空调", "开启空调", "开空调", "空调打开", "把空调打开", "空调开开", "开下空调") ->
                action("air_conditioner_on", "air_conditioner", "on")
            matchesAny(normalized, "关闭空调", "关空调", "空调关闭", "把空调关掉", "空调关了", "关掉空调") ->
                action("air_conditioner_off", "air_conditioner", "off")
            matchesAny(normalized, "调高温度", "升高温度", "温度调高", "热一点", "太冷了", "温度高一点") ->
                action("temperature_up", "temperature", "up")
            matchesAny(normalized, "调低温度", "降低温度", "温度调低", "冷一点", "太热了", "温度低一点") ->
                action("temperature_down", "temperature", "down")
            matchesAny(normalized, "打开车窗", "开车窗", "车窗打开", "把车窗打开", "降下车窗", "车窗降下来") ->
                action("window_open", "window", "open")
            matchesAny(normalized, "关闭车窗", "关车窗", "车窗关闭", "把车窗关上", "升起车窗", "车窗升起来") ->
                action("window_close", "window", "close")
            matchesAny(normalized, "切换场景", "切到场景", "切换模式") ->
                action("scene_switch", "scene", "switch")
            matchesAny(normalized, "当前车速", "车速多少", "速度多少", "速度怎样", "现在速度", "跑多快") ||
                topicQuery(normalized, SPEED_TERMS, listOf("多少", "多快", "当前", "现在", "怎样", "怎么样")) ->
                query("vehicle_speed_query", "speed")
            matchesAny(normalized, "当前档位", "什么档位", "现在档位", "挂的什么档", "当前单位", "什么单位", "现在单位") ||
                topicQuery(normalized, GEAR_TERMS, listOf("什么", "当前", "现在", "多少", "挂", "状态")) ->
                query("vehicle_gear_query", "gear")
            matchesAny(normalized, "电量多少", "还有多少电", "电池电量", "soc多少", "soc") ||
                topicQuery(normalized, BATTERY_TERMS, listOf("多少", "当前", "现在", "还有", "剩", "状态")) ->
                query("vehicle_battery_query", "battery")
            matchesAny(normalized, "剩余里程", "还能跑多远", "续航多少", "可跑里程") ||
                topicQuery(normalized, RANGE_TERMS, listOf("多少", "多远", "还能", "剩", "当前", "现在")) ->
                query("vehicle_range_query", "range")
            matchesAny(normalized, "空调状态", "空调开了吗", "空调是否开启", "当前空调") ||
                topicQuery(normalized, AC_TERMS, listOf("状态", "开", "关", "当前", "现在", "是否", "吗")) ->
                query("vehicle_ac_query", "air_conditioner")
            matchesAny(normalized, "车内温度", "车外温度", "当前温度", "温度多少", "设定温度") ||
                topicQuery(normalized, TEMPERATURE_TERMS, listOf("多少", "当前", "现在", "车内", "车外", "设定", "状态")) ->
                query("vehicle_temperature_query", "temperature")
            matchesAny(normalized, "车门关了吗", "车门是否关闭", "门关了吗", "前门状态", "中门状态") ||
                topicQuery(normalized, DOOR_TERMS, listOf("关", "开", "状态", "是否", "当前", "现在", "吗")) ->
                query("vehicle_door_query", "door")
            matchesAny(normalized, "当前位置", "定位在哪", "现在位置", "航向多少") ||
                topicQuery(normalized, LOCATION_TERMS, listOf("哪", "哪里", "当前", "现在", "多少", "状态")) ->
                query("vehicle_location_query", "location")
            matchesAny(normalized, "前方有没有障碍物", "有没有障碍物", "最近障碍物", "前方目标") ||
                topicQuery(normalized, OBSTACLE_TERMS, listOf("有没有", "有", "前方", "最近", "目标", "状态")) ->
                query("vehicle_obstacle_query", "obstacle")
            matchesAny(normalized, "红绿灯", "交通灯", "信号灯") ||
                topicQuery(normalized, TRAFFIC_LIGHT_TERMS, listOf("什么", "当前", "现在", "颜色", "状态", "红", "绿", "灯")) ->
                query("vehicle_traffic_light_query", "traffic_light")
            matchesAny(normalized, "胎压正常吗", "胎压状态", "轮胎状态", "轮胎正常吗", "有没有胎压报警") ||
                topicQuery(normalized, TIRE_TERMS, listOf("正常", "状态", "告警", "报警", "多少", "当前", "现在", "吗")) ->
                query("vehicle_tire_query", "tire")
            matchesAny(normalized, "智能驾驶状态", "自动驾驶状态", "l2状态", "辅助驾驶状态", "智驾状态") ||
                topicQuery(normalized, INTELLIGENT_DRIVING_TERMS, listOf("状态", "当前", "现在", "怎样", "怎么样", "正常")) ->
                query("vehicle_intelligent_status_query", "intelligent_driving")
            matchesAny(normalized, "acc状态", "acc怎么样", "自适应巡航状态", "巡航状态") ||
                topicQuery(normalized, ACC_TERMS, listOf("状态", "怎样", "怎么样", "当前", "现在", "正常")) ->
                query("vehicle_acc_query", "acc")
            matchesAny(normalized, "lka状态", "lka怎么样", "车道保持状态", "车道保持怎么样") ||
                topicQuery(normalized, LKA_TERMS, listOf("状态", "怎样", "怎么样", "当前", "现在", "正常")) ->
                query("vehicle_lka_query", "lka")
            matchesAny(normalized, "为什么不能进入自动驾驶", "不能进入自动驾驶", "自动驾驶进不去", "限制进入自动驾驶", "为什么进不了自动驾驶") ||
                (hasAny(normalized, AUTO_DRIVING_TERMS) && hasAny(normalized, listOf("不能进", "进不去", "进不了", "限制进入", "无法进入"))) ->
                query("vehicle_autod_limit_query", "autod_limit_reason")
            matchesAny(normalized, "为什么退出自动驾驶", "自动驾驶为什么退出", "退出自动驾驶原因", "为什么退出来") ||
                (hasAny(normalized, AUTO_DRIVING_TERMS) && hasAny(normalized, listOf("退出", "退出来", "为什么退", "原因"))) ->
                query("vehicle_autod_out_query", "autod_out_reason")
            matchesAny(normalized, "有没有需要接管", "需要接管吗", "接管提醒", "是否需要接管") ||
                topicQuery(normalized, TAKEOVER_TERMS, listOf("需要", "有没有", "是否", "提醒", "吗", "当前", "现在")) ->
                query("vehicle_takeover_query", "takeover")
            matchesAny(normalized, "有故障吗", "有没有故障", "当前故障", "车辆告警", "有什么告警", "有没有告警", "急停了吗") ||
                topicQuery(normalized, FAULT_TERMS, listOf("有", "有没有", "什么", "当前", "现在", "吗", "状态")) ->
                query("vehicle_fault_query", "fault")
            matchesAny(
                normalized,
                "当前协作场景",
                "协作场景是什么",
                "现在什么协作场景",
                "现在是什么协作场景",
                "协作是什么场景",
                "v2v还是v2i",
                "v2v或v2i",
                "v2v和v2i"
            ) ->
                query("vehicle_cooperation_scene_query", "cooperation_scene")
            cooperationSceneQuery(normalized) ->
                query("vehicle_cooperation_scene_query", "cooperation_scene")
            matchesAny(normalized, "协作事件开始了吗", "协作事件状态", "协作进行了吗", "协作结束了吗", "协作开始了吗", "协作状态") ||
                (hasAny(normalized, COOPERATION_TERMS) && hasAny(normalized, EVENT_TERMS + listOf("开始", "结束", "进行", "状态"))) ->
                query("vehicle_cooperation_event_query", "cooperation_event")
            matchesAny(normalized, "几辆协作车", "多少协作车", "协作车数量", "现在有几辆协作车", "有几辆协作车") ||
                (hasAny(normalized, COOPERATION_TERMS) && hasAny(normalized, listOf("车", "车辆")) && hasAny(normalized, listOf("几", "多少", "数量", "几个"))) ->
                query("vehicle_cooperation_count_query", "cooperation_count")
            matchesAny(normalized, "引导决策", "协作反馈", "反馈结果", "当前协作行为", "协作行为是什么", "协作决策") ||
                (hasAny(normalized, COOPERATION_TERMS + DECISION_TERMS) && hasAny(normalized, listOf("引导", "决策", "反馈", "结果", "行为", "当前", "现在", "什么"))) ->
                query("vehicle_cooperation_decision_query", "cooperation_decision")
            matchesAny(
                normalized,
                "查询状态",
                "当前状态",
                "车机状态",
                "现在状态",
                "车辆状态",
                "当前车辆状态",
                "检查车辆状态",
                "检查当前车辆状态",
                "查看车辆状态",
                "确认车辆状态"
            ) ||
                topicQuery(normalized, VEHICLE_STATUS_TERMS, listOf("检查", "查看", "确认", "当前", "现在", "查询", "状态")) ->
                query("status_query", "vehicle_state")
            else -> fallback("no_rule_match")
        }
    }

    private fun normalize(text: String): String {
        var normalized = text
            .lowercase()
            .replace(Regex("[\\s，。,.！？!?:：;；\\-]+"), "")
            .replace("一下", "")
            .replace("请", "")

        DOMAIN_TERM_CORRECTIONS.forEach { (from, to) ->
            normalized = normalized.replace(from, to)
        }
        return normalized
    }

    private fun matchesAny(text: String, vararg needles: String): Boolean = needles.any { text.contains(it) }

    private fun hasAny(text: String, needles: List<String>): Boolean = needles.any { text.contains(it) }

    private fun topicQuery(text: String, topicTerms: List<String>, queryTerms: List<String> = COMMON_QUERY_TERMS): Boolean {
        return hasAny(text, topicTerms) && hasAny(text, queryTerms)
    }

    private fun cooperationSceneQuery(text: String): Boolean {
        val sceneSignal = hasAny(text, SCENE_TERMS) || hasAny(text, listOf("v2v", "v2i", "v2x"))
        val questionSignal = hasAny(text, listOf("什么", "当前", "现在", "类型", "还是", "或", "和"))
        return hasAny(text, COOPERATION_TERMS) && sceneSignal && questionSignal
    }

    private fun action(name: String, target: String, operation: String): ParseResult = ParseResult(
        VoiceIntent(name, listOf(Slot("target", target), Slot("operation", operation))),
        replyKey = name,
        confidence = 0.95
    )

    private fun query(name: String, target: String): ParseResult = ParseResult(
        VoiceIntent(name, listOf(Slot("target", target)), mutatesVehicleState = false),
        replyKey = name,
        confidence = 0.92
    )

    private fun fallback(reason: String): ParseResult = ParseResult(
        VoiceIntent.Fallback,
        replyKey = "fallback",
        confidence = 0.0,
        reason = reason
    )

    private fun containsUnsafeText(text: String): Boolean {
        return UNSAFE_TOKENS.any { token -> text.contains(token) }
    }

    companion object {
        private val DOMAIN_TERM_CORRECTIONS = listOf(
            // General vehicle domain homophones / near words observed or expected from small
            // offline Chinese ASR. These are still gated by downstream topic+question matching.
            "小撤" to "小车",
            "小彻" to "小车",
            "车机" to "车机",
            "车辆" to "车辆",
            "撤辆" to "车辆",
            "彻辆" to "车辆",
            "车素" to "车速",
            "车数" to "车速",
            "车宿" to "车速",
            "车诉" to "车速",
            "测数" to "车速",
            "测速" to "车速",
            "时数" to "时速",
            "速读" to "速度",
            "数度" to "速度",
            "档为" to "档位",
            "挡位" to "档位",
            "挡为" to "档位",
            "党位" to "档位",
            "电亮" to "电量",
            "店量" to "电量",
            "电梁" to "电量",
            "电粮" to "电量",
            "soc" to "soc",
            "续行" to "续航",
            "序航" to "续航",
            "里成" to "里程",
            "历程" to "里程",
            "空条" to "空调",
            "空跳" to "空调",
            "空掉" to "空调",
            "空套" to "空调",
            "问度" to "温度",
            "温都" to "温度",
            "车们" to "车门",
            "撤门" to "车门",
            "前们" to "前门",
            "中们" to "中门",
            "位子" to "位置",
            "为止" to "位置",
            "航像" to "航向",
            "航象" to "航向",
            "障爱物" to "障碍物",
            "张碍物" to "障碍物",
            "长碍物" to "障碍物",
            "障碍我" to "障碍物",
            "红路灯" to "红绿灯",
            "红女灯" to "红绿灯",
            "交通等" to "交通灯",
            "信号等" to "信号灯",
            "胎呀" to "胎压",
            "胎牙" to "胎压",
            "胎鸭" to "胎压",
            "胎亚" to "胎压",
            "台压" to "胎压",
            "胎雅" to "胎压",
            "报景" to "报警",
            "抱警" to "报警",
            "告井" to "告警",
            "告紧" to "告警",
            "故章" to "故障",
            "古障" to "故障",
            "制动驾驶" to "自动驾驶",
            "自动加驶" to "自动驾驶",
            "自动加时" to "自动驾驶",
            "智架" to "智驾",
            "支架" to "智驾",
            "接官" to "接管",
            "借管" to "接管",
            "监管" to "接管",
            "巡行" to "巡航",
            "寻航" to "巡航",
            "车道保持" to "车道保持",
            "车到保持" to "车道保持",
            "引到" to "引导",
            "引道" to "引导",
            "绝策" to "决策",
            "觉策" to "决策",
            "回馈" to "反馈",
            // Vosk small-cn often maps “协作” to homophones or nearby high-frequency words.
            // Keep these corrections domain-scoped by relying on downstream vehicle/cooperation
            // keywords such as 场景/事件/车/反馈/行为 before an intent is returned.
            "写作" to "协作",
            "协做" to "协作",
            "协坐" to "协作",
            "协助" to "协作",
            "协同" to "协作",
            "合作" to "协作",
            "协调" to "协作",
            "做作" to "协作",
            "场境" to "场景",
            "常见" to "场景",
            "场见" to "场景",
            "长景" to "场景",
            "长见" to "场景",
            "厂景" to "场景",
            "场警" to "场景",
            "常景" to "场景",
            "v二v" to "v2v",
            "v二i" to "v2i",
            "v二一" to "v2i",
            "v二x" to "v2x",
            "v突v" to "v2v",
            "v突i" to "v2i",
            "v突x" to "v2x"
        )

        private val COMMON_QUERY_TERMS = listOf("当前", "现在", "多少", "什么", "怎样", "怎么样", "状态", "有没有", "是否", "吗", "正常", "原因", "哪", "哪里")
        private val SPEED_TERMS = listOf("车速", "速度", "时速", "跑多快")
        private val GEAR_TERMS = listOf("档位", "档", "单位")
        private val BATTERY_TERMS = listOf("电量", "电池", "soc", "电")
        private val RANGE_TERMS = listOf("剩余里程", "里程", "续航", "还能跑", "可跑")
        private val AC_TERMS = listOf("空调", "风机", "制冷", "制热")
        private val TEMPERATURE_TERMS = listOf("温度", "车内", "车外", "设定")
        private val DOOR_TERMS = listOf("车门", "前门", "中门", "门")
        private val LOCATION_TERMS = listOf("位置", "定位", "经度", "纬度", "航向")
        private val OBSTACLE_TERMS = listOf("障碍物", "障碍", "目标", "前方目标")
        private val TRAFFIC_LIGHT_TERMS = listOf("红绿灯", "交通灯", "信号灯", "灯色")
        private val TIRE_TERMS = listOf("胎压", "轮胎", "胎")
        private val INTELLIGENT_DRIVING_TERMS = listOf("智能驾驶", "自动驾驶", "辅助驾驶", "智驾", "l2")
        private val AUTO_DRIVING_TERMS = listOf("自动驾驶", "智能驾驶", "智驾")
        private val ACC_TERMS = listOf("acc", "自适应巡航", "巡航")
        private val LKA_TERMS = listOf("lka", "车道保持")
        private val TAKEOVER_TERMS = listOf("接管", "接管提醒")
        private val FAULT_TERMS = listOf("故障", "告警", "报警", "急停", "异常")
        private val COOPERATION_TERMS = listOf("协作", "v2v", "v2i", "v2x")
        private val SCENE_TERMS = listOf("场景", "类型")
        private val EVENT_TERMS = listOf("事件", "时间", "状态")
        private val DECISION_TERMS = listOf("引导", "决策", "反馈", "结果", "行为")
        private val VEHICLE_STATUS_TERMS = listOf("车辆状态", "车况", "整车状态")

        private val UNSAFE_TOKENS = listOf(
            "忽略",
            "删除",
            "联网",
            "上传",
            "导出",
            "系统指令",
            "prompt",
            "注入",
            "sudo",
            "rmrf",
            "rm",
            "curl",
            "wget"
        ).map { raw ->
            raw.lowercase().replace(Regex("[\\s，。,.！？!?:：;；\\-]+"), "")
        }
    }
}

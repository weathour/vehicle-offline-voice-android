package com.company.vehiclevoice.nlu

object AskableVoiceContent {
    enum class CapabilityLevel(val label: String) {
        Stable("稳定"),
        Caveated("带条件")
    }

    data class Question(
        val phrase: String,
        val intent: String,
        val answerScope: String,
        val caveat: String? = null,
        val level: CapabilityLevel = CapabilityLevel.Stable
    )

    data class Category(
        val title: String,
        val description: String,
        val questions: List<Question>
    )

    val categories: List<Category> = listOf(
        Category(
            title = "六类只读查询",
            description = "可以换一种说法，识别不确定时会请你重新说。",
            questions = listOf(
                Question("车速多少", "vehicle_speed_query", "最新车速"),
                Question("电量多少", "vehicle_battery_query", "SOC 百分比"),
                Question("障碍物情况", "vehicle_obstacle_query", "障碍物数量和最近目标距离"),
                Question(
                    "协同模块状态",
                    "vehicle_sam_status_query",
                    "自动等级、驾驶模式反馈、档位、转向和 SAM 车速",
                    "缺少 scene_id 时不判断 V2V/V2I",
                    CapabilityLevel.Caveated
                ),
                Question("规划轨迹有多少点", "vehicle_trajectory_query", "轨迹点数和近似长度"),
                Question(
                    "红绿灯什么状态",
                    "vehicle_traffic_light_query",
                    "颜色、置信度和相位",
                    "只有时间戳时会明确说明业务数据不可用",
                    CapabilityLevel.Caveated
                )
            )
        )
    )

    val supportedQuestions: List<Question> = categories.flatMap { it.questions }
}

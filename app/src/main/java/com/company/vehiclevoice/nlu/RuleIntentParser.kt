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
            matchesAny(normalized, "查询状态", "当前状态", "车机状态", "现在状态") ->
                ParseResult(
                    VoiceIntent("status_query", listOf(Slot("target", "vehicle_state")), mutatesVehicleState = false),
                    replyKey = "status_query",
                    confidence = 0.92
                )
            else -> fallback("no_rule_match")
        }
    }

    private fun normalize(text: String): String = text
        .lowercase()
        .replace(Regex("[\\s，。,.！？!?:：;；\\-]+"), "")
        .replace("一下", "")
        .replace("请", "")

    private fun matchesAny(text: String, vararg needles: String): Boolean = needles.any { text.contains(it) }

    private fun action(name: String, target: String, operation: String): ParseResult = ParseResult(
        VoiceIntent(name, listOf(Slot("target", target), Slot("operation", operation))),
        replyKey = name,
        confidence = 0.95
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

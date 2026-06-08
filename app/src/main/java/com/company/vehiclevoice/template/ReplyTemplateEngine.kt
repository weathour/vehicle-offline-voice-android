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
        "unsafe_rejected" -> "该指令不属于离线车控范围，已拒绝执行"
        else -> "没有识别到有效指令"
    }

    private fun formatState(state: Map<String, String>): String =
        if (state.isEmpty()) "暂无状态" else state.entries.joinToString("，") { "${it.key}=${it.value}" }
}

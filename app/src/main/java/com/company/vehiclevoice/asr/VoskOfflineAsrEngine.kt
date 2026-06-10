package com.company.vehiclevoice.asr

import com.company.vehiclevoice.audio.PcmFrame
import com.company.vehiclevoice.audio.toLittleEndianPcm16Bytes
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

/** Real offline ASR adapter backed by a local Vosk model directory. */
class VoskOfflineAsrEngine(
    modelPath: String,
    private val sampleRateHz: Float = PcmFrame.DEFAULT_SAMPLE_RATE_HZ.toFloat(),
    private val grammar: List<String>? = null
) : AsrEngine, AutoCloseable {
    private val model: Model

    init {
        val modelDir = File(modelPath)
        require(modelDir.exists() && modelDir.isDirectory) {
            "Vosk model directory is missing: $modelPath. Package or copy an offline model before using VoskOfflineAsrEngine."
        }
        model = Model(modelDir.absolutePath)
    }

    override fun recognize(frames: List<PcmFrame>): AsrResult {
        if (frames.isEmpty()) return AsrResult(text = "", confidence = 0.0)
        newRecognizer().use { recognizer ->
            val bytes = frames.toLittleEndianPcm16Bytes()
            recognizer.acceptWaveForm(bytes, bytes.size)
            val json = recognizer.finalResult
            val text = extractJsonField(json, "text")
            return AsrResult(text = text, confidence = if (text.isBlank()) 0.0 else 0.80)
        }
    }

    override fun close() {
        model.close()
    }

    private fun newRecognizer(): Recognizer = if (grammar.isNullOrEmpty()) {
        Recognizer(model, sampleRateHz)
    } else {
        Recognizer(model, sampleRateHz, grammarJson(grammar + "[unk]"))
    }

    private fun grammarJson(phrases: List<String>): String =
        phrases.joinToString(prefix = "[", postfix = "]") { phrase ->
            "\"${phrase.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        }

    private fun extractJsonField(json: String, field: String): String {
        val match = Regex("\\\"$field\\\"\\s*:\\s*\\\"(.*?)\\\"").find(json) ?: return ""
        return match.groupValues[1]
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .trim()
    }

    companion object {
        val DEFAULT_COMMAND_GRAMMAR = listOf(
            "打开空调",
            "开启空调",
            "关闭空调",
            "关掉空调",
            "打开车窗",
            "关闭车窗",
            "调高温度",
            "调低温度",
            "查询状态",
            "当前状态",
            "Redis状态",
            "Redis健康",
            "数据健康",
            "实车数据健康",
            "数据新鲜度",
            "时间戳状态",
            "Key诊断",
            "缺哪些Key",
            "当前车速多少",
            "车速多少",
            "速度多少",
            "当前档位",
            "电量多少",
            "还有多少电",
            "电池详情",
            "电池电压",
            "电池电流",
            "剩余里程",
            "还能跑多远",
            "空调状态",
            "空调开了吗",
            "当前温度",
            "车内温度",
            "车门关了吗",
            "当前位置",
            "RTK状态",
            "RTK标志",
            "车辆姿态",
            "俯仰横滚",
            "前方有没有障碍物",
            "障碍物数量",
            "最近障碍物",
            "红绿灯",
            "交通灯",
            "车道线状态",
            "车道线数量",
            "规划轨迹",
            "轨迹点",
            "胎压正常吗",
            "胎压状态",
            "智能驾驶状态",
            "自动驾驶状态",
            "ACC状态",
            "LKA状态",
            "为什么不能进入自动驾驶",
            "为什么退出自动驾驶",
            "有没有需要接管",
            "当前有什么告警",
            "有没有告警",
            "SAM状态",
            "SAM反馈",
            "协作模块状态",
            "当前协作场景是什么",
            "协作场景是什么",
            "现在是什么协作场景",
            "现在什么协作场景",
            "当前写作场景是什么",
            "写作场景是什么",
            "协同场景是什么",
            "合作场景是什么",
            "V2V还是V2I",
            "协作事件开始了吗",
            "协作事件状态",
            "协作进行了吗",
            "协作结束了吗",
            "现在有几辆协作车",
            "几辆协作车",
            "协作车数量",
            "现在有几辆写作车",
            "引导决策是什么",
            "协作反馈结果是什么",
            "当前协作行为是什么",
            "写作反馈结果是什么",
            "瑞迪斯状态",
            "瑞迪思健康",
            "二梯开状态",
            "阿提开标志",
            "山姆状态",
            "三姆反馈",
            "车到线状态",
            "归迹点",
            "新鲜读"
        )
    }
}

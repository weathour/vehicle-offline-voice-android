package com.company.vehiclevoice.asr

import com.company.vehiclevoice.audio.PcmFrame
import com.company.vehiclevoice.audio.toLittleEndianPcm16Bytes
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

/** Real offline ASR adapter backed by a local Vosk model directory. */
class VoskOfflineAsrEngine(
    modelPath: String,
    private val sampleRateHz: Float = PcmFrame.DEFAULT_SAMPLE_RATE_HZ.toFloat(),
    private val grammar: List<String>? = null,
    private val maxAlternatives: Int = 5
) : AsrEngine, AutoCloseable {
    private val model: Model

    init {
        val modelDir = File(modelPath)
        require(modelDir.exists() && modelDir.isDirectory) {
            "Vosk model directory is missing: $modelPath. Package or copy an offline model before using VoskOfflineAsrEngine."
        }
        require(maxAlternatives > 0) { "maxAlternatives must be positive" }
        model = Model(modelDir.absolutePath)
    }

    override fun recognize(frames: List<PcmFrame>): AsrResult {
        if (frames.isEmpty()) return AsrResult(text = "", confidence = 0.0)
        newRecognizer().use { recognizer ->
            val bytes = frames.toLittleEndianPcm16Bytes()
            recognizer.acceptWaveForm(bytes, bytes.size)
            return parseResultJson(recognizer.finalResult)
        }
    }

    override fun close() {
        model.close()
    }

    private fun newRecognizer(): Recognizer = (if (grammar.isNullOrEmpty()) {
        Recognizer(model, sampleRateHz)
    } else {
        Recognizer(model, sampleRateHz, grammarJson(grammar + "[unk]"))
    }).apply {
        setMaxAlternatives(maxAlternatives)
    }

    private fun grammarJson(phrases: List<String>): String =
        phrases.joinToString(prefix = "[", postfix = "]") { phrase ->
            "\"${phrase.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        }

    companion object {
        /** Character/word segmented because Vosk runtime grammar splits phrases on spaces. */
        val SIX_QUERY_GRAMMAR = listOf(
            // 车速
            "车 速", "车 速 多 少", "当 前 车 速", "现 在 车 速", "速 度 多 少", "跑 多 快",
            "车 素 多 少", "车 数 多 少", "车 宿 多 少", "测 速 多 少", "时 速 多 少",
            // 电量
            "电 量", "电 量 多 少", "当 前 电 量", "还 有 多 少 电", "剩 多 少 电", "电 池 电 量",
            "店 量 多 少", "电 亮 多 少", "电 粮 多 少", "艾 斯 欧 西",
            // 障碍物
            "障 碍 物", "障 碍 物 情 况", "有 没 有 障 碍 物", "前 方 障 碍 物", "最 近 障 碍 物", "障 碍 物 数 量",
            "障 爱 物 情 况", "张 碍 物 情 况", "长 碍 物 情 况", "障 碍 我 情 况",
            // 协同模块 / Sensor_SAM
            "协 同 模 块", "协 同 模 块 状 态", "协 作 模 块 状 态", "合 作 模 块 状 态", "协 同 状 态",
            "SAM 状 态", "山 姆 状 态", "三 姆 状 态", "萨 姆 状 态",
            // 规划轨迹点
            "规 划 轨 迹", "规 划 轨 迹 点", "轨 迹 点", "轨 迹 点 多 少", "轨 迹 有 多 少 点", "当 前 轨 迹",
            "归 迹 点", "规 迹 点", "轨 机 点", "诡 计 点",
            // 红绿灯
            "红 绿 灯", "红 绿 灯 状 态", "红 绿 灯 什 么 状 态", "红 绿 灯 什 么 颜 色", "交 通 灯 状 态", "信 号 灯 状 态",
            "红 路 灯 状 态", "红 女 灯 状 态", "交 通 等 状 态", "信 号 等 状 态"
        )

        internal fun parseResultJson(json: String): AsrResult = runCatching {
            val root = JSONObject(json)
            val array = root.optJSONArray("alternatives")
            val alternatives = buildList {
                if (array != null) {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        add(
                            AsrAlternative(
                                text = item.optString("text", "").trim(),
                                confidence = item.optDouble("confidence", 0.0)
                            )
                        )
                    }
                }
            }
            val primary = alternatives.firstOrNull()
            val text = primary?.text ?: root.optString("text", "").trim()
            AsrResult(
                text = text,
                confidence = primary?.confidence ?: if (text.isBlank()) 0.0 else 0.80,
                alternatives = alternatives
            )
        }.getOrElse {
            val text = extractJsonField(json, "text")
            AsrResult(text = text, confidence = if (text.isBlank()) 0.0 else 0.80)
        }

        private fun extractJsonField(json: String, field: String): String {
            val match = Regex("\\\"$field\\\"\\s*:\\s*\\\"(.*?)\\\"").find(json) ?: return ""
            return match.groupValues[1]
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .trim()
        }
    }
}

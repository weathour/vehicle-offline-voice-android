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
    private val grammar: List<String>? = DEFAULT_COMMAND_GRAMMAR
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
        val DEFAULT_COMMAND_GRAMMAR = listOf("打开空调", "关闭空调", "打开车窗", "关闭车窗", "调高温度", "调低温度", "查询状态")
    }
}

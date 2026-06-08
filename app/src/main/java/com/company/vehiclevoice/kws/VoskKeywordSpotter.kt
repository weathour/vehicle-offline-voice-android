package com.company.vehiclevoice.kws

import com.company.vehiclevoice.audio.PcmFrame
import com.company.vehiclevoice.audio.toLittleEndianPcm16Bytes
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.util.Locale

/**
 * Real offline phrase-spotting adapter backed by Vosk ASR with a restricted wake grammar.
 *
 * This is phrase spotting over an offline recognizer, not a production-grade low-power KWS model.
 * It requires a local filesystem model path and never performs runtime downloads.
 */
class VoskKeywordSpotter(
    modelPath: String,
    private val wakePhrases: List<String> = listOf("你好车机", "你好 车机", "小车小车", "小车 小车"),
    private val sampleRateHz: Float = PcmFrame.DEFAULT_SAMPLE_RATE_HZ.toFloat(),
    private val minConfidence: Double = 0.50,
    private val useRestrictedGrammar: Boolean = false
) : KeywordSpotter, AutoCloseable {
    private val model: Model
    private var recognizer: Recognizer

    init {
        require(wakePhrases.isNotEmpty()) { "wakePhrases must not be empty" }
        val modelDir = File(modelPath)
        require(modelDir.exists() && modelDir.isDirectory) {
            "Vosk model directory is missing: $modelPath. Package or copy an offline model before using VoskKeywordSpotter."
        }
        model = Model(modelDir.absolutePath)
        recognizer = newRecognizer()
    }

    override fun accept(frame: PcmFrame): KeywordEvent {
        val bytes = frame.toLittleEndianPcm16Bytes()
        val accepted = recognizer.acceptWaveForm(bytes, bytes.size)
        val json = if (accepted) recognizer.result else recognizer.partialResult
        val rawText = extractJsonField(json, if (accepted) "text" else "partial")
        val text = rawText.normalized()
        val matched = wakePhrases.firstOrNull { phrase -> text.contains(phrase.normalized()) }
        return if (matched != null) {
            KeywordEvent.Wake(matched, minConfidence.coerceIn(0.0, 1.0), frame.sequence)
        } else {
            KeywordEvent.None
        }
    }

    override fun reset() {
        recognizer.close()
        recognizer = newRecognizer()
    }

    override fun close() {
        recognizer.close()
        model.close()
    }

    private fun newRecognizer(): Recognizer = if (useRestrictedGrammar) {
        Recognizer(model, sampleRateHz, grammarJson(wakePhrases))
    } else {
        Recognizer(model, sampleRateHz)
    }

    private fun grammarJson(phrases: List<String>): String =
        (phrases + "[unk]").joinToString(prefix = "[", postfix = "]") { phrase ->
            "\"${phrase.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        }

    private fun extractJsonField(json: String, field: String): String {
        val match = Regex("\\\"$field\\\"\\s*:\\s*\\\"(.*?)\\\"").find(json) ?: return ""
        return match.groupValues[1]
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    private fun String.normalized(): String = lowercase(Locale.ROOT).replace(Regex("[\\s，。,.！？!?:：;；\\-]+"), "")
}

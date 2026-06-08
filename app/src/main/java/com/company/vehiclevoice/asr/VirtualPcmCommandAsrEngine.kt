package com.company.vehiclevoice.asr

import com.company.vehiclevoice.audio.PcmFrame
import com.company.vehiclevoice.audio.VirtualTtsPcmSource
import kotlin.math.roundToInt

/** Test/smoke ASR that recognizes deterministic virtual-TTS PCM, not metadata labels. */
class VirtualPcmCommandAsrEngine(
    commands: List<String> = DEFAULT_COMMANDS,
    private val fallback: AsrResult = AsrResult(text = "", confidence = 0.0)
) : AsrEngine {
    private val commandSignatures: List<Pair<String, List<Int>>> = commands.map { command ->
        command to VirtualTtsPcmSource.framesForText(command).map { it.signature() }
    }

    override fun recognize(frames: List<PcmFrame>): AsrResult {
        val observed = frames.filterNot { it.isSilent() }.map { it.signature() }
        val match = commandSignatures.firstOrNull { (_, signature) -> observed.containsSubsequence(signature) }
        return if (match != null) AsrResult(text = match.first, confidence = 0.96) else fallback
    }

    private fun List<Int>.containsSubsequence(needle: List<Int>): Boolean {
        if (needle.isEmpty() || size < needle.size) return false
        return windowed(needle.size).any { it == needle }
    }

    private fun PcmFrame.signature(): Int = rms().roundToInt()

    companion object {
        val DEFAULT_COMMANDS = listOf("打开空调", "关闭空调", "打开车窗", "关闭车窗", "调高温度", "调低温度", "查询状态")
    }
}

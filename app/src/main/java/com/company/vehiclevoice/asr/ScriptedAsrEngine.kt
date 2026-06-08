package com.company.vehiclevoice.asr

import com.company.vehiclevoice.audio.PcmFrame

class ScriptedAsrEngine(
    responses: List<AsrResult>,
    private val fallback: AsrResult = AsrResult(text = "", confidence = 0.0)
) : AsrEngine {
    private val queue = ArrayDeque(responses)

    override fun recognize(frames: List<PcmFrame>): AsrResult =
        if (frames.isEmpty()) fallback else queue.removeFirstOrNull() ?: fallback

    companion object {
        fun single(text: String, confidence: Double = 0.98): ScriptedAsrEngine =
            ScriptedAsrEngine(listOf(AsrResult(text, confidence)))
    }
}

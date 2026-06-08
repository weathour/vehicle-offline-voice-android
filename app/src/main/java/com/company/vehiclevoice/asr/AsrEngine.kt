package com.company.vehiclevoice.asr

import com.company.vehiclevoice.audio.PcmFrame

interface AsrEngine {
    fun recognize(frames: List<PcmFrame>): AsrResult
}

data class AsrResult(
    val text: String,
    val confidence: Double = 1.0,
    val isFinal: Boolean = true
)

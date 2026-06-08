package com.company.vehiclevoice.vad

import com.company.vehiclevoice.audio.PcmFrame

interface VadEngine {
    fun accept(frame: PcmFrame): VadEvent
    fun reset()
}

sealed class VadEvent {
    data class Silence(val frame: PcmFrame, val rms: Double) : VadEvent()
    data class SpeechStart(
        val frame: PcmFrame,
        val rms: Double,
        val preRollFrames: List<PcmFrame> = emptyList()
    ) : VadEvent()
    data class Speech(val frame: PcmFrame, val rms: Double) : VadEvent()
    data class SpeechEnd(val frame: PcmFrame, val rms: Double) : VadEvent()
}

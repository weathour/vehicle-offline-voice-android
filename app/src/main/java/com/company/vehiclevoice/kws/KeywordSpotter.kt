package com.company.vehiclevoice.kws

import com.company.vehiclevoice.audio.PcmFrame

interface KeywordSpotter {
    fun accept(frame: PcmFrame): KeywordEvent
    fun reset() {}
}

sealed class KeywordEvent {
    data object None : KeywordEvent()
    data class Wake(val keyword: String, val confidence: Double, val frameSequence: Long) : KeywordEvent()
}

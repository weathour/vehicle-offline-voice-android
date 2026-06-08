package com.company.vehiclevoice.kws

import com.company.vehiclevoice.audio.PcmFrame

class ScriptedKeywordSpotter(
    private val wakeSequences: Set<Long>,
    private val keyword: String = "你好车机",
    private val confidence: Double = 0.99
) : KeywordSpotter {
    override fun accept(frame: PcmFrame): KeywordEvent =
        if (frame.sequence in wakeSequences) KeywordEvent.Wake(keyword, confidence, frame.sequence) else KeywordEvent.None
}

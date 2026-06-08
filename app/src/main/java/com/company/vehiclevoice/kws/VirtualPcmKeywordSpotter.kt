package com.company.vehiclevoice.kws

import com.company.vehiclevoice.audio.PcmFrame
import com.company.vehiclevoice.audio.VirtualTtsPcmSource
import kotlin.math.roundToInt

/** Test/smoke KWS that matches deterministic virtual-TTS PCM frames, not metadata labels. */
class VirtualPcmKeywordSpotter(
    private val wakePhrase: String = VirtualTtsPcmSource.DEFAULT_WAKE_PHRASE,
    private val confidence: Double = 0.97
) : KeywordSpotter {
    private val target = VirtualTtsPcmSource.framesForText(wakePhrase).map { it.signature() }
    private val recent = ArrayDeque<Int>()

    override fun accept(frame: PcmFrame): KeywordEvent {
        recent += frame.signature()
        while (recent.size > target.size) recent.removeFirst()
        return if (recent.size == target.size && recent.toList() == target) {
            recent.clear()
            KeywordEvent.Wake(wakePhrase, confidence, frame.sequence)
        } else {
            KeywordEvent.None
        }
    }

    override fun reset() {
        recent.clear()
    }

    private fun PcmFrame.signature(): Int = rms().roundToInt()
}

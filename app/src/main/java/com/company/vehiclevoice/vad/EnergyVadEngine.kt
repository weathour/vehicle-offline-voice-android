package com.company.vehiclevoice.vad

import com.company.vehiclevoice.audio.PcmFrame

class EnergyVadEngine(
    private val speechRmsThreshold: Double = 0.02,
    private val minSpeechFrames: Int = 2,
    private val endSilenceFrames: Int = 2
) : VadEngine {
    private var speechRun = 0
    private var silenceRun = 0
    private var inSpeech = false
    private val pendingSpeechFrames = mutableListOf<PcmFrame>()

    override fun accept(frame: PcmFrame): VadEvent {
        val rms = frame.normalizedRms()
        val active = rms >= speechRmsThreshold
        return if (active) {
            speechRun += 1
            pendingSpeechFrames += frame
            silenceRun = 0
            if (!inSpeech && speechRun >= minSpeechFrames) {
                inSpeech = true
                val preRoll = pendingSpeechFrames.toList()
                pendingSpeechFrames.clear()
                VadEvent.SpeechStart(frame, rms, preRollFrames = preRoll)
            } else if (inSpeech) {
                VadEvent.Speech(frame, rms)
            } else {
                VadEvent.Silence(frame, rms)
            }
        } else {
            speechRun = 0
            pendingSpeechFrames.clear()
            if (inSpeech) {
                silenceRun += 1
                if (silenceRun >= endSilenceFrames) {
                    inSpeech = false
                    silenceRun = 0
                    VadEvent.SpeechEnd(frame, rms)
                } else {
                    VadEvent.Speech(frame, rms)
                }
            } else {
                VadEvent.Silence(frame, rms)
            }
        }
    }

    override fun reset() {
        speechRun = 0
        silenceRun = 0
        inSpeech = false
        pendingSpeechFrames.clear()
    }
}

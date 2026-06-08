package com.company.vehiclevoice.audio

/**
 * Deterministic virtual microphone source for local smoke tests.
 *
 * It renders text into PCM-like frames so tests can exercise the same AudioSource boundary as a
 * microphone. It is not a production TTS engine and does not expose command labels to downstream
 * KWS/ASR components.
 */
class VirtualTtsPcmSource private constructor(frames: List<PcmFrame>) : AudioSource {
    private val delegate = FakePcmSource(frames)
    override val isStarted: Boolean get() = delegate.isStarted

    override fun start() = delegate.start()
    override fun read(): PcmFrame? = delegate.read()
    override fun stop() = delegate.stop()

    companion object {
        const val DEFAULT_WAKE_PHRASE = "你好车机"

        fun singleCommand(
            commandText: String,
            wakePhrase: String = DEFAULT_WAKE_PHRASE,
            leadingSilenceFrames: Int = 2,
            gapSilenceFrames: Int = 1,
            trailingSilenceFrames: Int = 3
        ): VirtualTtsPcmSource {
            var sequence = 0L
            val frames = buildList {
                repeat(leadingSilenceFrames) { add(PcmFrame.silence(sequence++)) }
                addAll(VirtualTtsPcmRenderer.renderText(wakePhrase, sequence).also { sequence += it.size })
                repeat(gapSilenceFrames) { add(PcmFrame.silence(sequence++)) }
                addAll(VirtualTtsPcmRenderer.renderText(commandText, sequence).also { sequence += it.size })
                repeat(trailingSilenceFrames) { add(PcmFrame.silence(sequence++)) }
            }
            return VirtualTtsPcmSource(frames)
        }

        fun framesForText(text: String, startSequence: Long = 0L): List<PcmFrame> =
            VirtualTtsPcmRenderer.renderText(text, startSequence)
    }
}

object VirtualTtsPcmRenderer {
    fun renderText(text: String, startSequence: Long = 0L): List<PcmFrame> {
        if (text.isBlank()) return emptyList()
        return text.mapIndexed { index, char ->
            PcmFrame.syntheticTone(
                sequence = startSequence + index,
                amplitude = amplitudeFor(char),
                period = periodFor(char)
            )
        }
    }

    private fun amplitudeFor(char: Char): Short = (6_000 + (char.code % 18) * 650).toShort()

    private fun periodFor(char: Char): Int = 4 + (char.code % 7)
}

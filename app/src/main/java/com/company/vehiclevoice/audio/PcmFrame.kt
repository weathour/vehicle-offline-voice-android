package com.company.vehiclevoice.audio

import kotlin.math.sqrt

data class PcmFrame(
    val samples: ShortArray,
    val sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ,
    val channels: Int = 1,
    val timestampMs: Long = 0L,
    val sequence: Long = 0L
) {
    init {
        require(sampleRateHz > 0) { "sampleRateHz must be positive" }
        require(channels > 0) { "channels must be positive" }
    }

    val sampleCount: Int get() = samples.size

    fun rms(): Double {
        if (samples.isEmpty()) return 0.0
        var sumSquares = 0.0
        for (sample in samples) {
            val value = sample.toDouble()
            sumSquares += value * value
        }
        return sqrt(sumSquares / samples.size)
    }

    fun normalizedRms(): Double = rms() / Short.MAX_VALUE.toDouble()

    fun isSilent(threshold: Double = 0.001): Boolean = normalizedRms() < threshold

    companion object {
        const val DEFAULT_SAMPLE_RATE_HZ = 16_000
        const val DEFAULT_FRAME_SAMPLES = 1_600

        fun silence(
            sequence: Long,
            sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ,
            sampleCount: Int = DEFAULT_FRAME_SAMPLES,
            timestampMs: Long = sequence * 100L
        ): PcmFrame = PcmFrame(
            samples = ShortArray(sampleCount),
            sampleRateHz = sampleRateHz,
            timestampMs = timestampMs,
            sequence = sequence
        )

        fun constantTone(
            sequence: Long,
            amplitude: Short,
            sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ,
            sampleCount: Int = DEFAULT_FRAME_SAMPLES,
            timestampMs: Long = sequence * 100L
        ): PcmFrame = PcmFrame(
            samples = ShortArray(sampleCount) { amplitude },
            sampleRateHz = sampleRateHz,
            timestampMs = timestampMs,
            sequence = sequence
        )
    }
}

package com.company.vehiclevoice.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

class AndroidAudioRecordSource(
    private val sampleRateHz: Int = PcmFrame.DEFAULT_SAMPLE_RATE_HZ,
    private val frameSamples: Int = PcmFrame.DEFAULT_FRAME_SAMPLES,
    private val permissionGranted: () -> Boolean
) : AudioSource {
    private var audioRecord: AudioRecord? = null
    private var sequence: Long = 0L
    override var isStarted: Boolean = false
        private set

    override fun start() {
        if (isStarted) return
        if (!permissionGranted()) {
            throw SecurityException("RECORD_AUDIO permission is required before starting AndroidAudioRecordSource")
        }
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(frameSamples * BYTES_PER_SAMPLE * 2)

        @Suppress("MissingPermission")
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("AudioRecord failed to initialize")
        }
        record.startRecording()
        audioRecord = record
        sequence = 0L
        isStarted = true
    }

    override fun read(): PcmFrame? {
        val record = audioRecord ?: return null
        val buffer = ShortArray(frameSamples)
        val count = record.read(buffer, 0, buffer.size)
        if (count <= 0) return null
        val samples = if (count == buffer.size) buffer else buffer.copyOf(count)
        return PcmFrame(
            samples = samples,
            sampleRateHz = sampleRateHz,
            timestampMs = System.currentTimeMillis(),
            sequence = sequence++
        )
    }

    override fun stop() {
        val record = audioRecord
        audioRecord = null
        if (record != null) {
            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
            } finally {
                record.release()
            }
        }
        isStarted = false
    }

    companion object {
        private const val BYTES_PER_SAMPLE = 2
    }
}

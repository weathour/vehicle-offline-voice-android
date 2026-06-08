package com.company.vehiclevoice.audio

class FakePcmSource(frames: List<PcmFrame>) : AudioSource {
    private val frames: List<PcmFrame> = frames.toList()
    private var index = 0
    override var isStarted: Boolean = false
        private set

    override fun start() {
        index = 0
        isStarted = true
    }

    override fun read(): PcmFrame? {
        check(isStarted) { "FakePcmSource must be started before read" }
        return frames.getOrNull(index++)
    }

    override fun stop() {
        isStarted = false
    }

    companion object {
        fun scripted(vararg frames: PcmFrame): FakePcmSource = FakePcmSource(frames.toList())
    }
}

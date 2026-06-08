package com.company.vehiclevoice.audio

interface AudioSource : AutoCloseable {
    val isStarted: Boolean
    fun start()
    fun read(): PcmFrame?
    fun stop()
    override fun close() = stop()
}

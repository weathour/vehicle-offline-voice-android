package com.company.vehiclevoice.action

interface UnityEventSink {
    fun send(json: String)
}

class RecordingUnityEventSink : UnityEventSink {
    private val events = mutableListOf<String>()
    override fun send(json: String) {
        events += json
    }
    fun events(): List<String> = events.toList()
    fun clear() = events.clear()
}

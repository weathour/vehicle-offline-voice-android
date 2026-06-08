package com.company.vehiclevoice.log

interface EventLogSink {
    fun info(message: String)
    fun warn(message: String)
    fun error(message: String, throwable: Throwable? = null)
}

class RecordingEventLogSink : EventLogSink {
    private val lines = mutableListOf<String>()
    override fun info(message: String) { lines += "INFO $message" }
    override fun warn(message: String) { lines += "WARN $message" }
    override fun error(message: String, throwable: Throwable?) { lines += "ERROR $message ${throwable?.message ?: ""}".trim() }
    fun lines(): List<String> = lines.toList()
}

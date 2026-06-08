package com.company.vehiclevoice.log

import com.company.vehiclevoice.UiLogBus
import com.company.vehiclevoice.VoiceLogger

class AndroidEventLogSink : EventLogSink {
    override fun info(message: String) {
        VoiceLogger.info(message)
        UiLogBus.publish(message)
    }

    override fun warn(message: String) {
        VoiceLogger.warn(message)
        UiLogBus.publish("WARN $message")
    }

    override fun error(message: String, throwable: Throwable?) {
        VoiceLogger.error(message, throwable)
        UiLogBus.publish("ERROR $message ${throwable?.message ?: ""}".trim())
    }
}

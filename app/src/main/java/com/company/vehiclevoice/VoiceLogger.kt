package com.company.vehiclevoice

import android.util.Log

object VoiceLogger {
    const val TAG = "VehicleVoice"

    fun info(message: String) = Log.i(TAG, message)
    fun warn(message: String) = Log.w(TAG, message)
    fun error(message: String, throwable: Throwable? = null) = Log.e(TAG, message, throwable)
}

package com.company.vehiclevoice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.Manifest
import android.content.pm.PackageManager
import com.company.vehiclevoice.asr.VoskModelAssetInstaller
import com.company.vehiclevoice.core.VoicePipelineController
import com.company.vehiclevoice.core.VoicePipelineFactory
import com.company.vehiclevoice.core.VoiceRuntimeMode
import com.company.vehiclevoice.log.AndroidEventLogSink

class VoiceForegroundService : Service() {
    private val logSink = AndroidEventLogSink()
    private var controller: VoicePipelineController? = null
    private var currentMode: VoiceRuntimeMode = VoiceRuntimeMode.PreviewMock

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        VoiceLogger.info("VoiceForegroundService created")
        controller = newController(currentMode)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requestedMode = VoiceRuntimeMode.fromWireValue(intent?.getStringExtra(VoiceRuntimeMode.EXTRA_NAME))
        VoiceLogger.info("VoiceForegroundService start command mode=${requestedMode.wireValue}")
        if (requestedMode != currentMode || controller == null) {
            controller?.close()
            currentMode = requestedMode
            controller = newController(requestedMode)
        }
        startForegroundWithMicrophoneType()
        controller?.start()
        return START_STICKY
    }

    override fun onDestroy() {
        VoiceLogger.info("VoiceForegroundService destroyed")
        controller?.close()
        controller = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null


    private fun newController(mode: VoiceRuntimeMode): VoicePipelineController = VoicePipelineController(
        pipelineFactory = {
            VoicePipelineFactory.createServicePipeline(
                mode = mode,
                logSink = logSink,
                realMicPermissionGranted = {
                    checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                },
                voskModelPath = {
                    runCatching { VoskModelAssetInstaller.ensureModelCopied(this).absolutePath }
                        .onFailure { logSink.warn("Vosk model unavailable: ${it.message}") }
                        .getOrNull()
                }
            )
        },
        logSink = logSink
    )

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(com.company.vehiclevoice.R.string.voice_service_notification_title))
            .setContentText(getString(com.company.vehiclevoice.R.string.voice_service_notification_text))
            .setOngoing(true)
            .build()
    }

    private fun startForegroundWithMicrophoneType() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(com.company.vehiclevoice.R.string.voice_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "vehicle_voice_service"
        private const val NOTIFICATION_ID = 1001
    }
}

package com.company.vehiclevoice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.company.vehiclevoice.asr.VoskModelAssetInstaller
import com.company.vehiclevoice.data.readonly.VehicleDataSourceMode
import com.company.vehiclevoice.data.readonly.VehicleDataSourceRuntimeConfig
import com.company.vehiclevoice.data.readonly.VehicleRedisKeys
import com.company.vehiclevoice.core.VoicePipelineController
import com.company.vehiclevoice.core.VoicePipelineFactory
import com.company.vehiclevoice.core.VoiceRuntimeMode
import com.company.vehiclevoice.data.readonly.RedisVehicleSnapshotProvider
import com.company.vehiclevoice.data.readonly.SocketRedisBinaryDataSource
import com.company.vehiclevoice.data.readonly.SocketRedisConfig
import com.company.vehiclevoice.log.AndroidEventLogSink
import com.company.vehiclevoice.tts.TtsProvider
import com.company.vehiclevoice.tts.createConfiguredTtsEngine

class VoiceForegroundService : Service() {
    private val logSink = AndroidEventLogSink()
    private var controller: VoicePipelineController? = null
    private var currentMode: VoiceRuntimeMode = VoiceRuntimeMode.PreviewMock
    private var currentVehicleSourceConfig: VehicleDataSourceRuntimeConfig = VehicleDataSourceRuntimeConfig()
    private var currentTtsProvider = TtsProvider.Edge
    private val healthHandler = Handler(Looper.getMainLooper())
    private val healthCheck = object : Runnable {
        override fun run() {
            if (checkPipelineHealth()) healthHandler.postDelayed(this, HEALTH_CHECK_INTERVAL_MS)
        }
    }
    private var recoveryAttempts = 0
    private var showingRecoveryNotification = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        VoiceLogger.info("VoiceForegroundService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            logSink.warn("VoiceForegroundService restart missing intent; stopping instead of falling back to preview mode")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        healthHandler.removeCallbacks(healthCheck)
        val requestedMode = VoiceRuntimeMode.fromWireValue(intent?.getStringExtra(VoiceRuntimeMode.EXTRA_NAME))
        val requestedVehicleSource = vehicleSourceConfigFromIntent(intent)
        val requestedTtsProvider = TtsProvider.fromWireValue(
            intent?.getStringExtra(EXTRA_TTS_PROVIDER) ?: currentTtsProvider.wireValue
        )
        VoiceLogger.info(
            "VoiceForegroundService start command mode=${requestedMode.wireValue} " +
                "vehicleSource=${requestedVehicleSource.displayName} " +
                "tts=${requestedTtsProvider.wireValue}"
        )
        if (requestedMode != currentMode || requestedVehicleSource != currentVehicleSourceConfig ||
            requestedTtsProvider != currentTtsProvider || controller == null
        ) {
            controller?.close()
            currentMode = requestedMode
            currentVehicleSourceConfig = requestedVehicleSource
            currentTtsProvider = requestedTtsProvider
            controller = newController(requestedMode, requestedVehicleSource, requestedTtsProvider)
        }
        recoveryAttempts = 0
        showingRecoveryNotification = false
        startForegroundForMode(requestedMode, requestedVehicleSource)
        controller?.start()
        if (requestedMode == VoiceRuntimeMode.RealMicManual) {
            healthHandler.postDelayed(healthCheck, HEALTH_CHECK_INTERVAL_MS)
        }
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        VoiceLogger.info("VoiceForegroundService destroyed")
        healthHandler.removeCallbacks(healthCheck)
        controller?.close()
        controller = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun newController(
        mode: VoiceRuntimeMode,
        vehicleSourceConfig: VehicleDataSourceRuntimeConfig,
        ttsProvider: TtsProvider
    ): VoicePipelineController = VoicePipelineController(
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
                },
                ttsEngineFactory = { createConfiguredTtsEngine(this, ttsProvider, logSink) },
                readOnlySnapshotProviderFactory = { readOnlySnapshotProvider(vehicleSourceConfig) }
            )
        },
        logSink = logSink,
        runConfigFactory = { VoicePipelineFactory.runConfigForMode(mode) }
    )

    private fun readOnlySnapshotProvider(config: VehicleDataSourceRuntimeConfig): RedisVehicleSnapshotProvider = when (config.mode) {
        VehicleDataSourceMode.Simulated -> RedisVehicleSnapshotProvider.simulated()
        VehicleDataSourceMode.RemoteRedis -> RedisVehicleSnapshotProvider(
            dataSource = SocketRedisBinaryDataSource(
                SocketRedisConfig(
                    host = config.host,
                    port = config.port,
                    password = config.password,
                    database = config.database,
                    timeoutMs = config.timeoutMs
                )
            ),
            keys = VehicleRedisKeys.fourQueryKeys,
            snapshotDeadlineMs = config.snapshotDeadlineMs
        )
    }

    private fun vehicleSourceConfigFromIntent(intent: Intent?): VehicleDataSourceRuntimeConfig {
        val mode = VehicleDataSourceMode.fromWireValue(
            intent?.getStringExtra(VehicleDataSourceRuntimeConfig.EXTRA_SOURCE_MODE)
        )
        val host = intent?.getStringExtra(VehicleDataSourceRuntimeConfig.EXTRA_REDIS_HOST)
            ?.takeIf { it.isNotBlank() }
            ?: VehicleDataSourceRuntimeConfig.DEFAULT_REMOTE_HOST
        val port = intent?.getIntExtra(VehicleDataSourceRuntimeConfig.EXTRA_REDIS_PORT, VehicleDataSourceRuntimeConfig.DEFAULT_REMOTE_PORT)
            ?: VehicleDataSourceRuntimeConfig.DEFAULT_REMOTE_PORT
        val password = intent?.getStringExtra(VehicleDataSourceRuntimeConfig.EXTRA_REDIS_PASSWORD)?.takeIf { it.isNotBlank() }
        val database = intent?.getIntExtra(VehicleDataSourceRuntimeConfig.EXTRA_REDIS_DATABASE, 0) ?: 0
        val timeout = intent?.getIntExtra(
            VehicleDataSourceRuntimeConfig.EXTRA_REDIS_TIMEOUT_MS,
            VehicleDataSourceRuntimeConfig.DEFAULT_TIMEOUT_MS
        ) ?: VehicleDataSourceRuntimeConfig.DEFAULT_TIMEOUT_MS
        return VehicleDataSourceRuntimeConfig(
            mode = mode,
            host = host,
            port = port,
            password = password,
            database = database,
            timeoutMs = timeout
        )
    }

    private fun buildNotification(
        contentText: String = getString(com.company.vehiclevoice.R.string.voice_service_notification_text)
    ): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setSmallIcon(R.drawable.ic_notification_voice)
            .setContentTitle(getString(com.company.vehiclevoice.R.string.voice_service_notification_title))
            .setContentText(contentText)
            .setOngoing(true)
            .build()
    }

    private fun checkPipelineHealth(): Boolean {
        if (currentMode != VoiceRuntimeMode.RealMicManual) return false
        return when (controller?.currentState()) {
            VoicePipelineController.State.Running -> {
                if (showingRecoveryNotification) {
                    updateNotification(getString(com.company.vehiclevoice.R.string.voice_service_notification_text))
                    showingRecoveryNotification = false
                }
                true
            }
            VoicePipelineController.State.Completed,
            VoicePipelineController.State.Failed,
            null -> recoverPipeline()
            else -> false
        }
    }

    private fun recoverPipeline(): Boolean {
        if (recoveryAttempts >= MAX_RECOVERY_ATTEMPTS) {
            logSink.error("Voice pipeline recovery exhausted after $recoveryAttempts attempts")
            updateNotification(getString(com.company.vehiclevoice.R.string.voice_service_notification_failed))
            return false
        }
        recoveryAttempts += 1
        logSink.warn("Voice pipeline stopped unexpectedly; recovery $recoveryAttempts/$MAX_RECOVERY_ATTEMPTS")
        updateNotification(
            getString(
                com.company.vehiclevoice.R.string.voice_service_notification_recovering,
                recoveryAttempts,
                MAX_RECOVERY_ATTEMPTS
            )
        )
        showingRecoveryNotification = true
        controller?.close()
        controller = newController(currentMode, currentVehicleSourceConfig, currentTtsProvider)
        controller?.start()
        return true
    }

    private fun updateNotification(contentText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    private fun startForegroundForMode(mode: VoiceRuntimeMode, vehicleSourceConfig: VehicleDataSourceRuntimeConfig) {
        val notification = buildNotification()
        logSink.info("Foreground service type=microphone mode=${mode.wireValue} vehicleSource=${vehicleSourceConfig.displayName}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
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
        const val EXTRA_TTS_PROVIDER = "com.company.vehiclevoice.EXTRA_TTS_PROVIDER"
        private const val CHANNEL_ID = "vehicle_voice_service"
        private const val NOTIFICATION_ID = 1001
        private const val HEALTH_CHECK_INTERVAL_MS = 2_000L
        private const val MAX_RECOVERY_ATTEMPTS = 3
    }
}

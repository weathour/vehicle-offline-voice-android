package com.company.vehiclevoice

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.company.vehiclevoice.core.VoiceRuntimeMode

class MainActivity : Activity() {
    private lateinit var logView: TextView
    private lateinit var statusPanel: TextView
    private lateinit var wakePanel: TextView
    private lateinit var asrPanel: TextView
    private lateinit var nluPanel: TextView
    private lateinit var ttsPanel: TextView
    private lateinit var rmsPanel: TextView
    private var pendingStartAfterPermission = false
    private var pendingModeAfterPermission: VoiceRuntimeMode = VoiceRuntimeMode.PreviewMock
    private val serviceLogListener: (String) -> Unit = { line ->
        runOnUiThread {
            updateDebugPanel(line)
            logView.append("$line\n")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContentView())
        appendLog("项目骨架已启动：VehicleOfflineVoice")
        appendLog("当前阶段：离线语音核心闭环调试；真实麦克风验证请看上方识别/TTS 面板。")
        appendLog("提示：面板会单独显示 KWS/ASR/NLU/TTS，底部保留完整日志。")
    }

    override fun onResume() {
        super.onResume()
        UiLogBus.addListener(serviceLogListener)
    }

    override fun onPause() {
        UiLogBus.removeListener(serviceLogListener)
        super.onPause()
    }

    private fun buildContentView(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val title = TextView(this).apply {
            text = "Vehicle Offline Voice"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
        }
        root.addView(title)

        root.addView(Button(this).apply {
            text = "启动 Mock 预览"
            setOnClickListener {
                startVoiceServiceWhenPermissionsReady(VoiceRuntimeMode.PreviewMock)
            }
        })

        root.addView(Button(this).apply {
            text = "启动虚拟麦克风烟测"
            setOnClickListener {
                startVoiceServiceWhenPermissionsReady(VoiceRuntimeMode.VirtualMicSmoke)
            }
        })

        root.addView(Button(this).apply {
            text = "启动真实麦克风手动验证"
            setOnClickListener {
                startVoiceServiceWhenPermissionsReady(VoiceRuntimeMode.RealMicManual)
            }
        })

        root.addView(Button(this).apply {
            text = "停止语音服务"
            setOnClickListener { stopVoiceService() }
        })

        root.addView(Button(this).apply {
            text = "清空日志/面板"
            setOnClickListener {
                logView.text = ""
                resetDebugPanel()
                UiLogBus.clear()
            }
        })

        root.addView(buildDebugPanel())

        val logTitle = TextView(this).apply {
            text = "完整日志"
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
        }
        root.addView(logTitle)

        val scrollView = ScrollView(this)
        logView = TextView(this).apply {
            textSize = 12f
            setTextIsSelectable(true)
        }
        scrollView.addView(logView)
        root.addView(scrollView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        return root
    }

    private fun buildDebugPanel(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 18)
        }
        panel.addView(TextView(this).apply {
            text = "识别 / TTS 调试面板"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
        })
        statusPanel = debugLine("状态", "待启动")
        wakePanel = debugLine("唤醒", "未检测")
        asrPanel = debugLine("识别", "未识别")
        nluPanel = debugLine("意图", "无")
        ttsPanel = debugLine("回复", "无")
        rmsPanel = debugLine("音频", "无 RMS")
        panel.addView(statusPanel)
        panel.addView(wakePanel)
        panel.addView(asrPanel)
        panel.addView(nluPanel)
        panel.addView(ttsPanel)
        panel.addView(rmsPanel)
        return panel
    }

    private fun debugLine(label: String, value: String): TextView = TextView(this).apply {
        text = "$label：$value"
        textSize = 15f
        setTextIsSelectable(true)
    }

    private fun resetDebugPanel() {
        if (!::statusPanel.isInitialized) return
        statusPanel.text = "状态：待启动"
        wakePanel.text = "唤醒：未检测"
        asrPanel.text = "识别：未识别"
        nluPanel.text = "意图：无"
        ttsPanel.text = "回复：无"
        rmsPanel.text = "音频：无 RMS"
    }

    private fun updateDebugPanel(line: String) {
        when {
            "VoiceForegroundService start command" in line -> statusPanel.text = "状态：服务启动 ${line.after("mode=")}"
            "Foreground service type" in line -> statusPanel.text = "状态：前台麦克风服务已启动"
            "Pipeline state=listening_start" in line -> statusPanel.text = "状态：监听中，等待唤醒词"
            "Pipeline state=wake_detected" in line -> statusPanel.text = "状态：已唤醒，请说命令"
            "Pipeline state=recording_utterance" in line -> statusPanel.text = "状态：正在录制命令"
            "Pipeline state=recognizing" in line -> statusPanel.text = "状态：正在识别命令"
            "Pipeline state=listening_resume" in line -> statusPanel.text = "状态：回到监听，等待下一次唤醒"
            "VoicePipelineController failed" in line || "ERROR" in line -> statusPanel.text = "状态：错误 ${line.takeLast(80)}"
        }
        if ("KWS wake" in line) wakePanel.text = "唤醒：${line.after("keyword=").before(" confidence=")}"
        if ("ASR text=" in line) asrPanel.text = "识别：${line.after("ASR text=").before(" confidence=").ifBlank { "空结果" }}"
        if ("NLU intent=" in line) nluPanel.text = "意图：${line.after("NLU intent=").before(" reason=")}"
        if ("TTS reply=" in line) ttsPanel.text = "回复：${line.after("TTS reply=")}"
        if ("Audio frame=" in line) rmsPanel.text = "音频：${line.after("Audio frame=")}"
        if ("Vosk model unavailable" in line) statusPanel.text = "状态：Vosk 模型不可用"
    }

    private fun String.after(token: String): String = substringAfter(token, missingDelimiterValue = "")
    private fun String.before(token: String): String = substringBefore(token, missingDelimiterValue = this)

    private fun startVoiceServiceWhenPermissionsReady(mode: VoiceRuntimeMode) {
        val permissions = missingRuntimePermissions(mode)
        if (permissions.isNotEmpty()) {
            pendingStartAfterPermission = true
            pendingModeAfterPermission = mode
            requestPermissions(permissions.toTypedArray(), REQUEST_PERMISSIONS)
            appendLog("已请求运行时权限，授权后再启动服务：${permissions.joinToString()}")
            return
        }
        startVoiceService(mode)
    }

    private fun missingRuntimePermissions(mode: VoiceRuntimeMode): List<String> {
        val permissions = buildList {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.RECORD_AUDIO)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        return permissions
    }

    private fun startVoiceService(mode: VoiceRuntimeMode) {
        val intent = Intent(this, VoiceForegroundService::class.java).putExtra(VoiceRuntimeMode.EXTRA_NAME, mode.wireValue)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        appendLog("已发送启动前台服务命令：${mode.displayName}")
        statusPanel.text = "状态：启动命令已发送 ${mode.displayName}"
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_PERMISSIONS) return

        val denied = permissions.zip(grantResults.toTypedArray())
            .filter { it.second != PackageManager.PERMISSION_GRANTED }
            .map { it.first }

        if (denied.isEmpty()) {
            appendLog("运行时权限已授权")
            if (pendingStartAfterPermission) startVoiceService(pendingModeAfterPermission)
        } else {
            appendLog("权限被拒绝，未启动真实麦克风相关服务：${denied.joinToString()}")
            statusPanel.text = "状态：权限被拒绝"
        }
        pendingStartAfterPermission = false
        pendingModeAfterPermission = VoiceRuntimeMode.PreviewMock
    }

    private fun stopVoiceService() {
        stopService(Intent(this, VoiceForegroundService::class.java))
        appendLog("已发送停止服务命令")
        statusPanel.text = "状态：已发送停止服务命令"
    }

    private fun appendLog(message: String) {
        val line = "${System.currentTimeMillis()}  $message\n"
        if (::logView.isInitialized) logView.append(line)
        VoiceLogger.info(message)
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 2001
    }
}

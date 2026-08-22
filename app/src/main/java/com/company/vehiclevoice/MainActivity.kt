package com.company.vehiclevoice

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.company.vehiclevoice.data.readonly.RedisVehicleSnapshotProvider
import com.company.vehiclevoice.data.readonly.VehicleDataSourceMode
import com.company.vehiclevoice.data.readonly.VehicleDataSourceRuntimeConfig
import com.company.vehiclevoice.core.VoiceRuntimeMode
import com.company.vehiclevoice.data.readonly.SocketRedisBinaryDataSource
import com.company.vehiclevoice.data.readonly.SocketRedisConfig
import com.company.vehiclevoice.nlu.AskableVoiceContent
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val isDebugBuild: Boolean
        get() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private lateinit var logView: TextView
    private lateinit var statusPanel: TextView
    private lateinit var wakePanel: TextView
    private lateinit var asrPanel: TextView
    private lateinit var nluPanel: TextView
    private lateinit var ttsPanel: TextView
    private lateinit var rmsPanel: TextView
    private lateinit var vehiclePanel: TextView
    private lateinit var warningPanel: TextView
    private lateinit var cooperationPanel: TextView
    private lateinit var remoteRedisCheckBox: CheckBox
    private lateinit var redisHostInput: EditText
    private lateinit var redisPortInput: EditText
    private lateinit var redisDbInput: EditText
    private lateinit var redisPasswordInput: EditText
    private lateinit var connectionPanel: TextView
    private lateinit var developerPanel: LinearLayout
    private lateinit var developerToggleButton: Button
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
        appendLog("${getString(R.string.app_name)}已启动。")
        appendLog("离线语音服务已就绪，车辆数据通过只读 Redis 接口获取。")
    }

    override fun onResume() {
        super.onResume()
        UiLogBus.addListener(serviceLogListener)
    }

    override fun onPause() {
        UiLogBus.removeListener(serviceLogListener)
        super.onPause()
    }

    private fun buildContentView(): ScrollView {
        val page = ScrollView(this).apply {
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val title = TextView(this).apply {
            text = getString(R.string.software_name)
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
        }
        root.addView(title)
        root.addView(TextView(this).apply {
            text = "使用流程：连接车辆网络 → 配置 Redis → 检测数据连接 → 启动离线语音服务"
            textSize = 13f
        })
        root.addView(buildRedisConfigPanel())

        root.addView(Button(this).apply {
            text = "1. 检测车辆数据连接"
            setOnClickListener { checkVehicleDataConnection() }
        })

        root.addView(Button(this).apply {
            text = "2. 启动离线语音服务"
            setOnClickListener {
                remoteRedisCheckBox.isChecked = true
                saveRedisConfig()
                startVoiceServiceWhenPermissionsReady(VoiceRuntimeMode.RealMicManual)
            }
        })

        if (isDebugBuild) {
            root.addView(Button(this).apply {
                text = "Redis 调试页面（1Hz 对比）"
                setOnClickListener {
                    remoteRedisCheckBox.isChecked = true
                    saveRedisConfig()
                    startActivity(Intent(this@MainActivity, RedisDebugActivity::class.java))
                }
            })
        }

        root.addView(Button(this).apply {
            text = "停止语音服务"
            setOnClickListener { stopVoiceService() }
        })

        connectionPanel = debugLine("连接状态", "未检测")
        root.addView(connectionPanel)
        root.addView(buildAskableContentPanel())
        val debugPanel = buildDebugPanel()
        if (isDebugBuild) {
            root.addView(debugPanel)
            root.addView(buildDeveloperPanel())
        }

        logView = TextView(this).apply {
            textSize = 12f
            setTextIsSelectable(true)
        }
        if (isDebugBuild) {
            root.addView(TextView(this).apply {
                text = "现场日志"
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
            })
            val logScrollView = ScrollView(this)
            logScrollView.addView(logView)
            root.addView(logScrollView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(220)
            ))
        }

        page.addView(root)
        return page
    }

    private fun buildRedisConfigPanel(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)
        }
        panel.addView(TextView(this).apply {
            text = "车辆 / 电脑 Redis"
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
        })
        remoteRedisCheckBox = CheckBox(this).apply {
            text = "读取外部 Redis"
            isChecked = true
            visibility = View.GONE
        }
        panel.addView(remoteRedisCheckBox)
        panel.addView(TextView(this).apply {
            text = "数据源：车辆只读 Redis"
            textSize = 13f
        })
        redisHostInput = EditText(this).apply {
            hint = "Redis IP，例如 192.168.1.10"
            setText(loadString(PREF_REDIS_HOST, VehicleDataSourceRuntimeConfig.DEFAULT_REMOTE_HOST))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }
        panel.addView(redisHostInput)
        redisPortInput = EditText(this).apply {
            hint = "端口"
            setText(loadString(PREF_REDIS_PORT, VehicleDataSourceRuntimeConfig.DEFAULT_REMOTE_PORT.toString()))
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
        }
        panel.addView(redisPortInput)
        redisDbInput = EditText(this).apply {
            hint = "DB，默认 0"
            setText(loadString(PREF_REDIS_DB, "0"))
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
        }
        panel.addView(redisDbInput)
        redisPasswordInput = EditText(this).apply {
            hint = "密码，可留空"
            setText(if (isDebugBuild) loadString(PREF_REDIS_PASSWORD, "") else "")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        panel.addView(redisPasswordInput)
        panel.addView(TextView(this).apply {
            text = "设备必须与车辆 Redis 位于同一网络。连接检测通过后即可启动语音服务。"
            textSize = 12f
        })
        return panel
    }

    private fun buildAskableContentPanel(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 18, 0, 18)
        }
        panel.addView(TextView(this).apply {
            text = "可问内容"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
        })
        panel.addView(TextView(this).apply {
            text = "实车主流程只做 Redis 只读问答，缺字段会说明证据不足，不会写车控。"
            textSize = 13f
        })
        val categories = if (isDebugBuild) AskableVoiceContent.categories else AskableVoiceContent.releaseCategories
        categories.forEach { category ->
            panel.addView(TextView(this).apply {
                text = category.title
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, 12, 0, 2)
            })
            panel.addView(TextView(this).apply {
                text = category.description
                textSize = 12f
            })
            panel.addView(TextView(this).apply {
                text = category.questions.joinToString("\n") { question ->
                    val caveat = question.caveat?.let { "（$it）" }.orEmpty()
                    "• [${question.level.label}] ${question.phrase}：${question.answerScope}$caveat"
                }
                textSize = 13f
                setLineSpacing(dp(2).toFloat(), 1.0f)
                setTextIsSelectable(true)
            })
        }
        return panel
    }

    private fun buildDebugPanel(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 18)
        }
        panel.addView(TextView(this).apply {
            text = "识别 / TTS / 车况调试面板"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
        })
        statusPanel = debugLine("状态", "待启动")
        wakePanel = debugLine("唤醒", "未检测")
        asrPanel = debugLine("识别", "未识别")
        nluPanel = debugLine("意图", "无")
        ttsPanel = debugLine("回复", "无")
        rmsPanel = debugLine("音频", "无 RMS")
        vehiclePanel = debugLine("只读车况", "未读取")
        warningPanel = debugLine("告警解释", "未读取")
        cooperationPanel = debugLine("协作信息", "未读取")
        panel.addView(statusPanel)
        panel.addView(wakePanel)
        panel.addView(asrPanel)
        panel.addView(nluPanel)
        panel.addView(ttsPanel)
        panel.addView(rmsPanel)
        panel.addView(vehiclePanel)
        panel.addView(warningPanel)
        panel.addView(cooperationPanel)
        return panel
    }

    private fun buildDeveloperPanel(): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 10, 0, 10)
        }
        developerToggleButton = Button(this).apply {
            text = "显示开发调试入口"
            setOnClickListener {
                val show = developerPanel.visibility != View.VISIBLE
                developerPanel.visibility = if (show) View.VISIBLE else View.GONE
                text = if (show) "隐藏开发调试入口" else "显示开发调试入口"
            }
        }
        container.addView(developerToggleButton)
        developerPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        developerPanel.addView(Button(this).apply {
            text = "开发：启动 APK 内置模拟预览"
            setOnClickListener {
                remoteRedisCheckBox.isChecked = false
                startVoiceServiceWhenPermissionsReady(VoiceRuntimeMode.PreviewMock)
            }
        })
        developerPanel.addView(Button(this).apply {
            text = "开发：启动虚拟麦克风烟测"
            setOnClickListener {
                remoteRedisCheckBox.isChecked = false
                startVoiceServiceWhenPermissionsReady(VoiceRuntimeMode.VirtualMicSmoke)
            }
        })
        developerPanel.addView(Button(this).apply {
            text = "清空日志/面板"
            setOnClickListener {
                logView.text = ""
                resetDebugPanel()
                UiLogBus.clear()
                connectionPanel.text = "连接状态：未检测"
            }
        })
        container.addView(developerPanel)
        return container
    }

    private fun debugLine(label: String, value: String): TextView = TextView(this).apply {
        text = "$label：$value"
        textSize = 15f
        setTextIsSelectable(true)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun resetDebugPanel() {
        if (!::statusPanel.isInitialized) return
        statusPanel.text = "状态：待启动"
        wakePanel.text = "唤醒：未检测"
        asrPanel.text = "识别：未识别"
        nluPanel.text = "意图：无"
        ttsPanel.text = "回复：无"
        rmsPanel.text = "音频：无 RMS"
        vehiclePanel.text = "只读车况：未读取"
        warningPanel.text = "告警解释：未读取"
        cooperationPanel.text = "协作信息：未读取"
    }

    private fun updateDebugPanel(line: String) {
        when {
            "VoiceForegroundService start command" in line -> statusPanel.text = "状态：服务启动 ${line.after("mode=")}"
            "Foreground service type" in line -> statusPanel.text = "状态：前台麦克风服务已启动 ${line.after("vehicleSource=")}"
            "Pipeline state=listening_start" in line -> statusPanel.text = "状态：监听中，等待唤醒词"
            "Pipeline state=wake_detected" in line -> statusPanel.text = "状态：已唤醒，正在提示"
            "Pipeline state=awaiting_command_after_wake_ack" in line -> statusPanel.text = "状态：请说指令"
            "Pipeline state=recording_utterance" in line -> statusPanel.text = "状态：正在录制命令"
            "Pipeline state=recognizing" in line -> statusPanel.text = "状态：正在识别命令"
            "Pipeline state=listening_resume" in line -> statusPanel.text = "状态：回到监听，等待下一次唤醒"
            "VoicePipelineController failed" in line || "ERROR" in line -> statusPanel.text = "状态：错误 ${line.takeLast(80)}"
        }
        if ("KWS wake" in line) wakePanel.text = "唤醒：${line.after("keyword=").before(" confidence=")}"
        if ("ASR text=" in line) asrPanel.text = "识别：${line.after("ASR text=").before(" confidence=").ifBlank { "空结果" }}"
        if ("NLU intent=" in line) nluPanel.text = "意图：${line.after("NLU intent=").before(" reason=")}"
        if ("TTS wake_ack=" in line) ttsPanel.text = "回复：${line.after("TTS wake_ack=")}"
        if ("TTS reply=" in line) ttsPanel.text = "回复：${line.after("TTS reply=")}"
        if ("Audio frame=" in line) rmsPanel.text = "音频：${line.after("Audio frame=")}"
        if ("Vehicle read-only snapshot" in line) vehiclePanel.text = "只读车况：${line.after("Vehicle read-only snapshot ").before(" warning=").take(220)}"
        if (" warning=" in line) warningPanel.text = "告警解释：${line.after(" warning=").before(" cooperation=").take(180)}"
        if (" cooperation=" in line) cooperationPanel.text = "协作信息：${line.after(" cooperation=").take(180)}"
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
        val sourceConfig = selectedVehicleSourceConfig()
        saveRedisConfig()
        val intent = Intent(this, VoiceForegroundService::class.java)
            .putExtra(VoiceRuntimeMode.EXTRA_NAME, mode.wireValue)
            .putExtra(VehicleDataSourceRuntimeConfig.EXTRA_SOURCE_MODE, sourceConfig.mode.wireValue)
            .putExtra(VehicleDataSourceRuntimeConfig.EXTRA_REDIS_HOST, sourceConfig.host)
            .putExtra(VehicleDataSourceRuntimeConfig.EXTRA_REDIS_PORT, sourceConfig.port)
            .putExtra(VehicleDataSourceRuntimeConfig.EXTRA_REDIS_PASSWORD, sourceConfig.password ?: "")
            .putExtra(VehicleDataSourceRuntimeConfig.EXTRA_REDIS_DATABASE, sourceConfig.database)
            .putExtra(VehicleDataSourceRuntimeConfig.EXTRA_REDIS_TIMEOUT_MS, sourceConfig.timeoutMs)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        appendLog("已发送启动前台服务命令：${mode.displayName}；车况数据源：${sourceConfig.displayName}")
        statusPanel.text = "状态：启动命令已发送 ${mode.displayName} / ${sourceConfig.displayName}"
    }

    private fun selectedVehicleSourceConfig(): VehicleDataSourceRuntimeConfig {
        val host = redisHostInput.text.toString().trim().ifBlank { VehicleDataSourceRuntimeConfig.DEFAULT_REMOTE_HOST }
        val port = redisPortInput.text.toString().trim().toIntOrNull() ?: VehicleDataSourceRuntimeConfig.DEFAULT_REMOTE_PORT
        val database = redisDbInput.text.toString().trim().toIntOrNull() ?: 0
        val password = redisPasswordInput.text.toString().trim().takeIf { it.isNotBlank() }
        return if (remoteRedisCheckBox.isChecked) {
            VehicleDataSourceRuntimeConfig(
                mode = VehicleDataSourceMode.RemoteRedis,
                host = host,
                port = port,
                password = password,
                database = database,
                timeoutMs = VehicleDataSourceRuntimeConfig.DEFAULT_TIMEOUT_MS
            )
        } else {
            VehicleDataSourceRuntimeConfig()
        }
    }

    private fun checkVehicleDataConnection() {
        remoteRedisCheckBox.isChecked = true
        val config = selectedVehicleSourceConfig()
        saveRedisConfig()
        connectionPanel.text = "连接状态：正在检测 ${config.host}:${config.port}/db${config.database} ..."
        appendLog("开始检测车辆数据连接：${config.displayName}")
        thread(name = "vehicle-redis-connection-check") {
            val result = runCatching {
                val provider = RedisVehicleSnapshotProvider(
                    dataSource = SocketRedisBinaryDataSource(
                        SocketRedisConfig(
                            host = config.host,
                            port = config.port,
                            password = config.password,
                            database = config.database,
                            timeoutMs = config.timeoutMs
                        )
                    ),
                    snapshotDeadlineMs = config.snapshotDeadlineMs
                )
                provider.readSnapshot()
            }
            runOnUiThread {
                result.onSuccess { snapshot ->
                    val decoded = snapshot.keyStatuses.values.count { it.decoded }
                    val missing = snapshot.keyStatuses.values.count { !it.present }
                    val errors = snapshot.keyStatuses.values.count { it.present && !it.decoded }
                    val basic = listOfNotNull(
                        snapshot.speedKmh?.let { "车速${it}km/h" },
                        snapshot.gear?.let { "档位$it" },
                        snapshot.batterySocPercent?.let { "电量${it}%" },
                        snapshot.cooperativeState?.summary
                    ).joinToString("，").ifBlank { "暂无摘要" }
                    val message = "连接状态：connected=${snapshot.diagnostics.connected}，decoded=$decoded，missing=$missing，decodeError=$errors；$basic"
                    connectionPanel.text = message
                    vehiclePanel.text = "只读车况：${snapshot.diagnostics.detail} decoded=$decoded missing=$missing error=$errors"
                    cooperationPanel.text = "协作信息：${snapshot.cooperativeState?.summary ?: "未读取"}"
                    appendLog(message)
                    if (errors > 0 || missing > 0) {
                        appendLog("异常 key：${snapshot.keyStatuses.values.filter { !it.decoded }.take(8).joinToString { "${it.key}:${it.error}" }}")
                    }
                }.onFailure { throwable ->
                    val message = "连接状态：失败 ${throwable.message ?: throwable::class.java.simpleName}"
                    connectionPanel.text = message
                    statusPanel.text = "状态：Redis 连接失败"
                    appendLog(message)
                }
            }
        }
    }

    private fun saveRedisConfig() {
        val editor = getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(PREF_REDIS_HOST, redisHostInput.text.toString().trim())
            .putString(PREF_REDIS_PORT, redisPortInput.text.toString().trim())
            .putString(PREF_REDIS_DB, redisDbInput.text.toString().trim())
        if (isDebugBuild) {
            editor.putString(PREF_REDIS_PASSWORD, redisPasswordInput.text.toString())
        } else {
            editor.remove(PREF_REDIS_PASSWORD)
        }
        editor.apply()
    }

    private fun loadString(key: String, fallback: String): String =
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, fallback) ?: fallback

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

    private fun appendLog(line: String) {
        if (::logView.isInitialized) logView.append("$line\n")
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 42
        private const val PREFS = "vehicle_voice_prefs"
        private const val PREF_REDIS_HOST = "redis_host"
        private const val PREF_REDIS_PORT = "redis_port"
        private const val PREF_REDIS_DB = "redis_db"
        private const val PREF_REDIS_PASSWORD = "redis_password"
    }
}

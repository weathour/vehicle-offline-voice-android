package com.company.vehiclevoice

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import com.company.vehiclevoice.data.readonly.RedisVehicleSnapshotProvider
import com.company.vehiclevoice.data.readonly.VehicleDataSourceMode
import com.company.vehiclevoice.data.readonly.VehicleDataSourceRuntimeConfig
import com.company.vehiclevoice.core.VoiceRuntimeMode
import com.company.vehiclevoice.data.readonly.SocketRedisBinaryDataSource
import com.company.vehiclevoice.data.readonly.SocketRedisConfig
import com.company.vehiclevoice.log.AndroidEventLogSink
import com.company.vehiclevoice.nlu.AskableVoiceContent
import com.company.vehiclevoice.tts.OnlineTtsConfig
import com.company.vehiclevoice.tts.TtsConfigStore
import com.company.vehiclevoice.tts.TtsProvider
import com.company.vehiclevoice.tts.createConfiguredTtsEngine
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
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
    private lateinit var spokenTextPanel: TextView
    private lateinit var ttsConfigStatus: TextView
    private val ttsRadioButtons = linkedMapOf<TtsProvider, RadioButton>()
    private var ttsConfig = OnlineTtsConfig()
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
        appendLog("语音服务已就绪，识别离线运行，播报使用当前 TTS 设置。")
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
            text = "使用流程：连接车辆网络 → 配置 Redis → 选择语音 → 检测连接 → 启动服务"
            textSize = 13f
        })
        root.addView(buildSpokenTextPanel())
        root.addView(buildRedisConfigPanel())
        root.addView(buildTtsPanel())

        root.addView(Button(this).apply {
            text = "1. 检测车辆数据连接"
            setOnClickListener { checkVehicleDataConnection() }
        })

        root.addView(Button(this).apply {
            text = "2. 启动语音服务"
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
        asrPanel = debugLine("最近听到", "尚无识别结果").apply {
            setPadding(0, dp(12), 0, dp(12))
        }
        root.addView(asrPanel)
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

    private fun buildTtsPanel(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 4, 0, 16)
        }
        panel.addView(TextView(this).apply {
            text = "语音播报"
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
        })
        ttsConfig = loadTtsConfig()
        val group = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
        }
        TtsProvider.entries.forEach { provider ->
            val button = RadioButton(this).apply {
                id = View.generateViewId()
                text = when (provider) {
                    TtsProvider.Edge -> "Edge 在线语音（免注册，推荐）"
                    TtsProvider.Baidu -> "百度在线语音（需导入 API 配置）"
                    TtsProvider.Tencent -> "腾讯云在线语音（需导入 API 配置）"
                    TtsProvider.System -> "Android 系统语音"
                }
                minimumHeight = dp(48)
                isEnabled = ttsConfig.isConfigured(provider)
            }
            ttsRadioButtons[provider] = button
            group.addView(button)
        }
        val preferred = preferredTtsProvider()
        ttsRadioButtons[preferred.takeIf(ttsConfig::isConfigured) ?: TtsProvider.Edge]?.isChecked = true
        group.setOnCheckedChangeListener { _, checkedId ->
            ttsRadioButtons.entries.firstOrNull { it.value.id == checkedId }?.let {
                saveTtsPreference(it.key)
                updateTtsConfigStatus()
            }
        }
        panel.addView(group)
        panel.addView(Button(this).apply {
            text = "试听当前语音"
            minimumHeight = dp(48)
            setOnClickListener { previewTts(this) }
        })
        panel.addView(Button(this).apply {
            text = "导入 TTS 配置文件"
            minimumHeight = dp(48)
            setOnClickListener { chooseTtsConfigFile() }
        })
        panel.addView(Button(this).apply {
            text = "清除已导入的 TTS 配置"
            minimumHeight = dp(48)
            setOnClickListener { clearTtsConfig() }
        })
        ttsConfigStatus = TextView(this).apply {
            textSize = 12f
            setTextIsSelectable(true)
        }
        panel.addView(ttsConfigStatus)
        updateTtsConfigStatus()
        return panel
    }

    private fun buildSpokenTextPanel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(12), 0, dp(16))
        addView(TextView(this@MainActivity).apply {
            text = "当前播报内容"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
        })
        spokenTextPanel = TextView(this@MainActivity).apply {
            text = "尚无播报内容"
            textSize = 22f
            setLineSpacing(dp(4).toFloat(), 1.08f)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            minimumHeight = dp(112)
            background = getDrawable(R.drawable.speech_output_background)
            setTextIsSelectable(true)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        addView(
            spokenTextPanel,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        addView(TextView(this@MainActivity).apply {
            text = "即使设备没有可用 TTS 或网络异常，这里仍会显示本次应播报的完整文字。"
            textSize = 12f
            setPadding(0, dp(6), 0, 0)
        })
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
        nluPanel = debugLine("意图", "无")
        ttsPanel = debugLine("回复", "无")
        rmsPanel = debugLine("音频", "无 RMS")
        vehiclePanel = debugLine("只读车况", "未读取")
        warningPanel = debugLine("告警解释", "未读取")
        cooperationPanel = debugLine("协作信息", "未读取")
        panel.addView(statusPanel)
        panel.addView(wakePanel)
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
        asrPanel.text = "最近听到：尚无识别结果"
        nluPanel.text = "意图：无"
        ttsPanel.text = "回复：无"
        spokenTextPanel.text = "尚无播报内容"
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
            "TTS " in line && " failed" in line -> statusPanel.text = "状态：语音播报不可用，回答已显示"
            "VoicePipelineController failed" in line || "ERROR" in line -> statusPanel.text = "状态：错误 ${line.takeLast(80)}"
        }
        if ("KWS wake" in line) wakePanel.text = "唤醒：${line.after("keyword=").before(" confidence=")}"
        if ("ASR text=" in line) {
            asrPanel.text = "最近听到：${line.after("ASR text=").before(" confidence=").ifBlank { "未识别到语音" }}"
        }
        if ("NLU intent=" in line) nluPanel.text = "意图：${line.after("NLU intent=").before(" reason=")}"
        if ("TTS wake_ack=" in line) showSpokenText(line.after("TTS wake_ack="))
        if ("TTS reply=" in line) showSpokenText(line.after("TTS reply="))
        if ("Audio frame=" in line) rmsPanel.text = "音频：${line.after("Audio frame=")}"
        if ("Vehicle read-only snapshot" in line) vehiclePanel.text = "只读车况：${line.after("Vehicle read-only snapshot ").before(" warning=").take(220)}"
        if (" warning=" in line) warningPanel.text = "告警解释：${line.after(" warning=").before(" cooperation=").take(180)}"
        if (" cooperation=" in line) cooperationPanel.text = "协作信息：${line.after(" cooperation=").take(180)}"
        if ("Vosk model unavailable" in line) statusPanel.text = "状态：Vosk 模型不可用"
    }

    private fun String.after(token: String): String = substringAfter(token, missingDelimiterValue = "")
    private fun String.before(token: String): String = substringBefore(token, missingDelimiterValue = this)

    private fun showSpokenText(text: String) {
        val value = text.trim().ifBlank { "尚无播报内容" }
        spokenTextPanel.text = value
        ttsPanel.text = "回复：$value"
    }

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
            .putExtra(VoiceForegroundService.EXTRA_TTS_PROVIDER, selectedTtsProvider().wireValue)
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
        appendLog(
            "已发送启动前台服务命令：${mode.displayName}；车况数据源：${sourceConfig.displayName}；" +
                "语音：${selectedTtsName()}"
        )
        statusPanel.text = "状态：启动命令已发送 ${mode.displayName} / ${sourceConfig.displayName}"
    }

    private fun previewTts(button: Button) {
        val provider = selectedTtsProvider()
        saveTtsPreference(provider)
        val ttsName = selectedTtsName()
        button.isEnabled = false
        statusPanel.text = "状态：正在加载并试听$ttsName"
        showSpokenText(TTS_PREVIEW_TEXT)
        thread(name = "vehicle-tts-preview") {
            val result = runCatching {
                val engine = createConfiguredTtsEngine(this, provider, AndroidEventLogSink())
                try {
                    engine.speak(TTS_PREVIEW_TEXT)
                } finally {
                    if (engine is AutoCloseable) engine.close()
                }
            }
            runOnUiThread {
                button.isEnabled = true
                result.onSuccess {
                    statusPanel.text = "状态：试听完成 $ttsName"
                    appendLog("TTS 试听完成：$ttsName")
                }.onFailure { throwable ->
                    statusPanel.text = "状态：试听失败 ${throwable.message}"
                    appendLog("TTS 试听失败：${throwable.message}")
                }
            }
        }
    }

    private fun selectedTtsProvider(): TtsProvider = ttsRadioButtons.entries
        .firstOrNull { it.value.isChecked }
        ?.key
        ?: TtsProvider.Edge

    private fun selectedTtsName(): String = selectedTtsProvider().displayName

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

    private fun saveTtsPreference(provider: TtsProvider = selectedTtsProvider()) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(PREF_TTS_PROVIDER, provider.wireValue)
            .remove(PREF_SYSTEM_TTS)
            .apply()
    }

    private fun preferredTtsProvider(): TtsProvider {
        val preferences = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = preferences.getString(PREF_TTS_PROVIDER, null)
        if (saved != null) return TtsProvider.fromWireValue(saved)
        return if (preferences.getBoolean(PREF_SYSTEM_TTS, false)) TtsProvider.System else ttsConfig.defaultProvider
    }

    private fun loadTtsConfig(): OnlineTtsConfig = runCatching { TtsConfigStore(this).load() }
        .onFailure { appendLog("TTS 配置读取失败：${it.message}") }
        .getOrDefault(OnlineTtsConfig())

    private fun updateTtsConfigStatus(message: String? = null) {
        if (!::ttsConfigStatus.isInitialized) return
        val selected = selectedTtsProvider()
        ttsConfigStatus.text = message ?: buildString {
            append("当前：${selected.displayName}。")
            append("Edge 免注册但需要联网；")
            append("百度${if (ttsConfig.baidu == null) "未配置" else "已配置"}；")
            append("腾讯云${if (ttsConfig.tencent == null) "未配置" else "已配置"}。")
            append("在线语音失败会尝试系统 TTS；仍失败时播放固定提示，并保留屏幕文字。")
        }
    }

    private fun refreshTtsChoices(preferred: TtsProvider = ttsConfig.defaultProvider) {
        ttsRadioButtons.forEach { (provider, button) ->
            button.isEnabled = ttsConfig.isConfigured(provider)
        }
        val selected = preferred.takeIf(ttsConfig::isConfigured) ?: TtsProvider.Edge
        ttsRadioButtons[selected]?.isChecked = true
        saveTtsPreference(selected)
        updateTtsConfigStatus()
    }

    private fun chooseTtsConfigFile() {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
            },
            REQUEST_TTS_CONFIG
        )
    }

    private fun clearTtsConfig() {
        statusPanel.text = "状态：正在清除 TTS 配置"
        thread(name = "vehicle-tts-config-clear") {
            val result = runCatching { TtsConfigStore(this).clear() }
            runOnUiThread {
                result.onSuccess {
                    ttsConfig = OnlineTtsConfig()
                    refreshTtsChoices(TtsProvider.Edge)
                    statusPanel.text = "状态：TTS 配置已清除"
                }.onFailure { throwable ->
                    statusPanel.text = "状态：清除 TTS 配置失败 ${throwable.message}"
                }
            }
        }
    }

    @Deprecated("Uses the platform document picker without adding another dependency")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_TTS_CONFIG || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        statusPanel.text = "状态：正在导入 TTS 配置"
        thread(name = "vehicle-tts-config-import") {
            val result = runCatching {
                val config = OnlineTtsConfig.parse(readTtsConfig(uri))
                TtsConfigStore(this).save(config)
                config
            }
            runOnUiThread {
                result.onSuccess { config ->
                    ttsConfig = config
                    refreshTtsChoices(config.defaultProvider)
                    statusPanel.text = "状态：TTS 配置导入成功"
                    appendLog("TTS 配置导入成功，未记录凭据内容")
                }.onFailure { throwable ->
                    statusPanel.text = "状态：TTS 配置导入失败 ${throwable.message}"
                    updateTtsConfigStatus("配置未导入：${throwable.message}")
                }
            }
        }
    }

    private fun readTtsConfig(uri: Uri): String {
        val input = contentResolver.openInputStream(uri) ?: error("无法读取所选文件")
        val output = ByteArrayOutputStream()
        input.use {
            val buffer = ByteArray(4 * 1024)
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                require(output.size() + count <= OnlineTtsConfig.MAX_CONFIG_BYTES) {
                    "TTS 配置文件不能超过 32 KB"
                }
                output.write(buffer, 0, count)
            }
        }
        return String(output.toByteArray(), StandardCharsets.UTF_8)
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
        private const val PREF_TTS_PROVIDER = "tts_provider"
        private const val PREF_SYSTEM_TTS = "prefer_system_tts"
        private const val REQUEST_TTS_CONFIG = 43
        private const val TTS_PREVIEW_TEXT =
            "语音测试。当前车速三十二公里每小时，V2X connection is ready，Redis data is normal。"
    }
}

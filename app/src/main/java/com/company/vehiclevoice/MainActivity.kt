package com.company.vehiclevoice

import android.Manifest
import android.app.Activity
import android.content.res.ColorStateList
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
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
            setBackgroundColor(getColor(R.color.app_background))
        }
        val wideScreen = resources.configuration.screenWidthDp >= 600
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(32))
        }

        val title = TextView(this).apply {
            text = getString(R.string.software_name)
            textSize = if (wideScreen) 30f else 24f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(getColor(R.color.ink))
        }
        root.addView(title)
        root.addView(supportingText("连接车辆网络，确认 Redis 后启动语音服务。所有查询只读，不控制车辆。"))
        root.addView(buildStatusPanel(), sectionParams(18))
        root.addView(buildSpokenTextPanel(), sectionParams(16))
        val settingsSection = buildSettingsSection()
        val actionPanel = buildActionPanel()
        if (wideScreen) {
            root.addView(settingsSection, sectionParams(8))
            root.addView(actionPanel, sectionParams(12))
        } else {
            root.addView(actionPanel, sectionParams(8))
            root.addView(settingsSection, sectionParams(12))
        }
        root.addView(buildRuntimeFeedbackPanel(), sectionParams(12))
        root.addView(buildAskableContentPanel(), sectionParams(16))

        val debugPanel = buildDebugPanel()
        if (isDebugBuild) {
            root.addView(debugPanel, sectionParams(16))
            root.addView(buildDeveloperPanel(), sectionParams(8))
        }

        logView = TextView(this).apply {
            textSize = 14f
            setTextColor(getColor(R.color.ink))
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setTextIsSelectable(true)
        }
        if (isDebugBuild) {
            root.addView(sectionTitle("现场日志"), sectionParams(18))
            val logScrollView = ScrollView(this).apply {
                background = getDrawable(R.drawable.panel_background)
            }
            logScrollView.addView(logView)
            root.addView(logScrollView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(220)
            ).apply { topMargin = dp(8) })
        }

        val horizontalMargin = dp(if (wideScreen) 48 else 16)
        val contentWidth = minOf(
            resources.displayMetrics.widthPixels - horizontalMargin * 2,
            dp(960)
        ).coerceAtLeast(1)
        page.addView(
            root,
            FrameLayout.LayoutParams(contentWidth, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            }
        )
        return page
    }

    private fun buildStatusPanel(): TextView = TextView(this).also { statusPanel = it }.apply {
        text = "服务状态：待启动"
        textSize = 18f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(getColor(R.color.status_info_text))
        setPadding(dp(18), dp(16), dp(18), dp(16))
        minimumHeight = dp(60)
        gravity = Gravity.CENTER_VERTICAL
        background = getDrawable(R.drawable.status_info_background)
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }

    private fun buildSettingsSection(): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = getDrawable(R.drawable.panel_background)
        }
        content.addView(buildRedisConfigPanel())
        content.addView(View(this).apply {
            setBackgroundColor(getColor(R.color.border))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
        content.addView(buildTtsPanel(), sectionParams(18))

        val firstRun = !getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(PREF_REDIS_HOST)
        content.visibility = if (firstRun) View.VISIBLE else View.GONE
        val toggle = secondaryButton(if (firstRun) "收起连接与语音设置" else "修改连接与语音设置")
        toggle.setOnClickListener {
            val show = content.visibility != View.VISIBLE
            content.visibility = if (show) View.VISIBLE else View.GONE
            toggle.text = if (show) "收起连接与语音设置" else "修改连接与语音设置"
        }
        container.addView(toggle)
        container.addView(content, sectionParams(8))
        return container
    }

    private fun buildActionPanel(): LinearLayout {
        val horizontal = resources.configuration.screenWidthDp >= 600
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val checkButton = secondaryButton("检测车辆连接").apply {
            setOnClickListener { checkVehicleDataConnection(this) }
        }
        val startButton = primaryButton("启动语音服务").apply {
            setOnClickListener {
                remoteRedisCheckBox.isChecked = true
                if (selectedVehicleSourceConfig() != null) {
                    startVoiceServiceWhenPermissionsReady(VoiceRuntimeMode.RealMicManual)
                }
            }
        }
        val stopButton = secondaryButton("停止语音服务").apply {
            setOnClickListener { stopVoiceService() }
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(checkButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(startButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(8)
        })
        if (horizontal) {
            row.addView(stopButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(8)
            })
        }
        panel.addView(row)
        if (!horizontal) panel.addView(stopButton, sectionParams(8))
        return panel
    }

    private fun buildRuntimeFeedbackPanel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(18), dp(20), dp(18))
        background = getDrawable(R.drawable.panel_background)
        addView(sectionTitle("实时反馈"))
        connectionPanel = debugLine("车辆连接", "尚未检测")
        addView(connectionPanel, sectionParams(8))
        asrPanel = debugLine("最近听到", "尚无识别结果")
        addView(asrPanel, sectionParams(6))
    }

    private fun buildRedisConfigPanel(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(16))
        }
        panel.addView(sectionTitle("车辆数据连接"))
        remoteRedisCheckBox = CheckBox(this).apply {
            text = "读取外部 Redis"
            isChecked = true
            visibility = View.GONE
        }
        panel.addView(remoteRedisCheckBox)
        panel.addView(supportingText("数据源：车辆只读 Redis"))
        redisHostInput = EditText(this).apply {
            hint = "例如 192.168.2.112"
            setText(loadString(PREF_REDIS_HOST, VehicleDataSourceRuntimeConfig.DEFAULT_REMOTE_HOST))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }
        addLabeledInput(panel, "Redis 地址", redisHostInput)
        redisPortInput = EditText(this).apply {
            hint = "1–65535"
            setText(loadString(PREF_REDIS_PORT, VehicleDataSourceRuntimeConfig.DEFAULT_REMOTE_PORT.toString()))
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
        }
        addLabeledInput(panel, "端口", redisPortInput)
        redisDbInput = EditText(this).apply {
            hint = "0 或更大"
            setText(loadString(PREF_REDIS_DB, "0"))
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
        }
        addLabeledInput(panel, "数据库编号", redisDbInput)
        redisPasswordInput = EditText(this).apply {
            hint = "未设置可留空"
            setText(if (isDebugBuild) loadString(PREF_REDIS_PASSWORD, "") else "")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        addLabeledInput(panel, "密码（可选）", redisPasswordInput)
        panel.addView(supportingText("设备必须与车辆 Redis 位于同一网络。正式版密码只在本次运行中使用，不会保存。"), sectionParams(10))
        return panel
    }

    private fun buildTtsPanel(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
        }
        panel.addView(sectionTitle("语音播报"))
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
                textSize = 16f
                setTextColor(getColor(R.color.ink))
                minimumHeight = dp(52)
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
        panel.addView(secondaryButton("试听当前语音").apply {
            setOnClickListener { previewTts(this) }
        }, sectionParams(8))
        panel.addView(secondaryButton("导入 TTS 配置文件").apply {
            setOnClickListener { chooseTtsConfigFile() }
        }, sectionParams(8))
        panel.addView(secondaryButton("清除在线语音配置").apply {
            setOnClickListener { clearTtsConfig() }
        }, sectionParams(8))
        ttsConfigStatus = TextView(this).apply {
            textSize = 14f
            setTextColor(getColor(R.color.ink_muted))
            setLineSpacing(dp(2).toFloat(), 1.08f)
            setTextIsSelectable(true)
        }
        panel.addView(ttsConfigStatus, sectionParams(10))
        updateTtsConfigStatus()
        return panel
    }

    private fun buildSpokenTextPanel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(sectionTitle("当前播报内容"))
        spokenTextPanel = TextView(this@MainActivity).apply {
            text = "尚无播报内容"
            textSize = if (resources.configuration.screenWidthDp >= 600) 26f else 22f
            setTextColor(getColor(R.color.ink))
            setLineSpacing(dp(4).toFloat(), 1.08f)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            minimumHeight = dp(120)
            background = getDrawable(R.drawable.speech_output_background)
            setTextIsSelectable(true)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        addView(
            spokenTextPanel,
            sectionParams(8)
        )
        addView(
            supportingText("设备没有可用 TTS 或网络异常时，这里仍会显示完整回答。"),
            sectionParams(6)
        )
    }

    private fun buildAskableContentPanel(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(20))
            background = getDrawable(R.drawable.panel_background)
        }
        panel.addView(sectionTitle("可问内容"))
        panel.addView(
            supportingText("唤醒后可换一种说法询问以下六类信息，识别不确定时软件会请你重新说。"),
            sectionParams(4)
        )
        panel.addView(TextView(this).apply {
            text = AskableVoiceContent.supportedQuestions.joinToString("\n") { "• ${it.phrase}" }
            textSize = 18f
            setTextColor(getColor(R.color.ink))
            setLineSpacing(dp(7).toFloat(), 1.08f)
            setTextIsSelectable(true)
        }, sectionParams(12))
        panel.addView(
            supportingText("车辆端缺少对应字段或数据无效时，回答会明确说明当前不可用。"),
            sectionParams(10)
        )
        return panel
    }

    private fun buildDebugPanel(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = getDrawable(R.drawable.panel_background)
        }
        panel.addView(sectionTitle("识别 / TTS / 车况调试面板"))
        wakePanel = debugLine("唤醒", "未检测")
        nluPanel = debugLine("意图", "无")
        ttsPanel = debugLine("回复", "无")
        rmsPanel = debugLine("音频", "无 RMS")
        vehiclePanel = debugLine("只读车况", "未读取")
        warningPanel = debugLine("告警解释", "未读取")
        cooperationPanel = debugLine("协作信息", "未读取")
        listOf(wakePanel, nluPanel, ttsPanel, rmsPanel, vehiclePanel, warningPanel, cooperationPanel)
            .forEach { panel.addView(it, sectionParams(6)) }
        return panel
    }

    private fun buildDeveloperPanel(): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        developerToggleButton = secondaryButton("显示开发调试入口").apply {
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
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = getDrawable(R.drawable.panel_background)
        }
        developerPanel.addView(secondaryButton("打开 Redis 调试页面（1Hz）").apply {
            setOnClickListener {
                remoteRedisCheckBox.isChecked = true
                if (selectedVehicleSourceConfig() != null) {
                    saveRedisConfig()
                    startActivity(Intent(this@MainActivity, RedisDebugActivity::class.java))
                }
            }
        })
        developerPanel.addView(secondaryButton("启动 APK 内置模拟预览").apply {
            setOnClickListener {
                remoteRedisCheckBox.isChecked = false
                startVoiceServiceWhenPermissionsReady(VoiceRuntimeMode.PreviewMock)
            }
        }, sectionParams(8))
        developerPanel.addView(secondaryButton("启动虚拟麦克风烟测").apply {
            setOnClickListener {
                remoteRedisCheckBox.isChecked = false
                startVoiceServiceWhenPermissionsReady(VoiceRuntimeMode.VirtualMicSmoke)
            }
        }, sectionParams(8))
        developerPanel.addView(secondaryButton("清空日志与调试面板").apply {
            setOnClickListener {
                logView.text = ""
                resetDebugPanel()
                UiLogBus.clear()
                connectionPanel.text = "车辆连接：尚未检测"
            }
        }, sectionParams(8))
        container.addView(developerPanel, sectionParams(8))
        return container
    }

    private fun sectionTitle(value: String): TextView = TextView(this).apply {
        text = value
        textSize = 20f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(getColor(R.color.ink))
    }

    private fun supportingText(value: String): TextView = TextView(this).apply {
        text = value
        textSize = 15f
        setTextColor(getColor(R.color.ink_muted))
        setLineSpacing(dp(2).toFloat(), 1.08f)
    }

    private fun addLabeledInput(parent: LinearLayout, label: String, input: EditText) {
        parent.addView(TextView(this).apply {
            text = label
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(getColor(R.color.ink))
            labelFor = input.id.takeIf { it != View.NO_ID } ?: View.generateViewId().also { input.id = it }
        }, sectionParams(12))
        input.apply {
            textSize = 16f
            minimumHeight = dp(52)
            setTextColor(getColor(R.color.ink))
            setHintTextColor(getColor(R.color.ink_muted))
        }
        parent.addView(input)
    }

    private fun primaryButton(label: String): Button = Button(this).apply {
        text = label
        textSize = 16f
        isAllCaps = false
        minimumHeight = dp(56)
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(getColor(R.color.on_primary))
        backgroundTintList = ColorStateList.valueOf(getColor(R.color.primary))
    }

    private fun secondaryButton(label: String): Button = Button(this).apply {
        text = label
        textSize = 16f
        isAllCaps = false
        minimumHeight = dp(56)
        setTextColor(getColor(R.color.ink))
        backgroundTintList = ColorStateList.valueOf(getColor(R.color.surface_muted))
    }

    private fun debugLine(label: String, value: String): TextView = TextView(this).apply {
        text = "$label：$value"
        textSize = 16f
        setTextColor(getColor(R.color.ink))
        setLineSpacing(dp(2).toFloat(), 1.06f)
        setTextIsSelectable(true)
    }

    private fun sectionParams(topMarginDp: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(topMarginDp)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private enum class StatusTone { Info, Success, Warning, Error }

    private fun showStatus(message: String, tone: StatusTone = StatusTone.Info) {
        if (!::statusPanel.isInitialized) return
        val (textColor, background) = when (tone) {
            StatusTone.Info -> R.color.status_info_text to R.drawable.status_info_background
            StatusTone.Success -> R.color.status_success_text to R.drawable.status_success_background
            StatusTone.Warning -> R.color.status_warning_text to R.drawable.status_warning_background
            StatusTone.Error -> R.color.status_error_text to R.drawable.status_error_background
        }
        statusPanel.text = "服务状态：$message"
        statusPanel.setTextColor(getColor(textColor))
        statusPanel.background = getDrawable(background)
    }

    private fun resetDebugPanel() {
        if (!::statusPanel.isInitialized) return
        showStatus("待启动")
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
            "VoiceForegroundService start command" in line -> showStatus("正在启动语音服务")
            "Foreground service type" in line -> showStatus("语音服务已启动，正在准备麦克风")
            "Pipeline state=listening_start" in line -> showStatus("监听中，等待唤醒词", StatusTone.Success)
            "Pipeline state=wake_detected" in line -> showStatus("已唤醒，正在提示", StatusTone.Success)
            "Pipeline state=awaiting_command_after_wake_ack" in line -> showStatus("请说问题", StatusTone.Success)
            "Pipeline state=recording_utterance" in line -> showStatus("正在录制问题", StatusTone.Success)
            "Pipeline state=recognizing" in line -> showStatus("正在识别问题")
            "Pipeline state=listening_resume" in line -> showStatus("监听中，等待下一次唤醒", StatusTone.Success)
            "TTS " in line && " failed" in line -> showStatus("语音播报不可用，回答已显示", StatusTone.Error)
            "VoicePipelineController failed" in line || "ERROR" in line ->
                showStatus("运行错误：${line.takeLast(80)}", StatusTone.Error)
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
        if ("Vosk model unavailable" in line) showStatus("离线识别模型不可用", StatusTone.Error)
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
        val sourceConfig = selectedVehicleSourceConfig() ?: return
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
        startForegroundService(intent)
        appendLog(
            "已发送启动前台服务命令：${mode.displayName}；车况数据源：${sourceConfig.displayName}；" +
                "语音：${selectedTtsName()}"
        )
        showStatus("启动命令已发送，正在准备服务")
    }

    private fun previewTts(button: Button) {
        val provider = selectedTtsProvider()
        saveTtsPreference(provider)
        val ttsName = selectedTtsName()
        button.isEnabled = false
        showStatus("正在加载并试听$ttsName")
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
                    showStatus("试听完成：$ttsName", StatusTone.Success)
                    appendLog("TTS 试听完成：$ttsName")
                }.onFailure { throwable ->
                    showStatus("试听失败：${throwable.message}", StatusTone.Error)
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

    private fun selectedVehicleSourceConfig(): VehicleDataSourceRuntimeConfig? {
        if (!remoteRedisCheckBox.isChecked) return VehicleDataSourceRuntimeConfig()

        redisHostInput.error = null
        redisPortInput.error = null
        redisDbInput.error = null
        val host = redisHostInput.text.toString().trim()
        val port = redisPortInput.text.toString().trim().toIntOrNull()?.takeIf { it in 1..65535 }
        val database = redisDbInput.text.toString().trim().toIntOrNull()?.takeIf { it >= 0 }
        if (host.isBlank()) redisHostInput.error = "请输入 Redis 地址"
        if (port == null) redisPortInput.error = "端口必须是 1–65535"
        if (database == null) redisDbInput.error = "数据库编号必须是 0 或更大的整数"
        if (host.isBlank() || port == null || database == null) {
            showStatus("Redis 配置有误，请检查输入框提示", StatusTone.Error)
            return null
        }
        val password = redisPasswordInput.text.toString().trim().takeIf { it.isNotBlank() }
        return VehicleDataSourceRuntimeConfig(
            mode = VehicleDataSourceMode.RemoteRedis,
            host = host,
            port = port,
            password = password,
            database = database,
            timeoutMs = VehicleDataSourceRuntimeConfig.DEFAULT_TIMEOUT_MS
        )
    }

    private fun checkVehicleDataConnection(button: Button) {
        remoteRedisCheckBox.isChecked = true
        val config = selectedVehicleSourceConfig() ?: return
        saveRedisConfig()
        button.isEnabled = false
        button.text = "正在检测…"
        showStatus("正在检测车辆数据连接")
        connectionPanel.text = "车辆连接：正在检测 ${config.host}:${config.port}/db${config.database}…"
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
                button.isEnabled = true
                button.text = "检测车辆连接"
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
                    val connected = snapshot.diagnostics.connected
                    val message = "车辆连接：${if (connected) "已连接" else "异常"}；已解码 $decoded，缺失 $missing，解码失败 $errors\n$basic"
                    connectionPanel.text = message
                    vehiclePanel.text = "只读车况：${snapshot.diagnostics.detail} decoded=$decoded missing=$missing error=$errors"
                    cooperationPanel.text = "协作信息：${snapshot.cooperativeState?.summary ?: "未读取"}"
                    when {
                        !connected -> showStatus("Redis 连接异常，请检查网络和配置", StatusTone.Error)
                        decoded == 0 || errors > 0 || missing > 0 ->
                            showStatus("已连接 Redis，但车辆数据不完整（已解码 $decoded）", StatusTone.Warning)
                        else -> showStatus("车辆数据连接正常，可以启动语音服务", StatusTone.Success)
                    }
                    appendLog(message)
                    if (errors > 0 || missing > 0) {
                        appendLog("异常 key：${snapshot.keyStatuses.values.filter { !it.decoded }.take(8).joinToString { "${it.key}:${it.error}" }}")
                    }
                }.onFailure { throwable ->
                    val message = "车辆连接：失败\n${throwable.message ?: throwable::class.java.simpleName}\n请确认设备与 Redis 同网，并检查地址和端口。"
                    connectionPanel.text = message
                    showStatus("车辆数据连接失败，请检查网络和配置", StatusTone.Error)
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
            appendLine("当前语音：${selected.displayName}")
            append("Edge 需要联网；百度${if (ttsConfig.baidu == null) "未配置" else "已配置"}，")
            appendLine("腾讯云${if (ttsConfig.tencent == null) "未配置" else "已配置"}。")
            append("在线语音失败时会尝试系统 TTS，仍失败则播放固定提示并保留屏幕文字。")
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
        showStatus("正在清除 TTS 配置")
        thread(name = "vehicle-tts-config-clear") {
            val result = runCatching { TtsConfigStore(this).clear() }
            runOnUiThread {
                result.onSuccess {
                    ttsConfig = OnlineTtsConfig()
                    refreshTtsChoices(TtsProvider.Edge)
                    showStatus("TTS 配置已清除", StatusTone.Success)
                }.onFailure { throwable ->
                    showStatus("清除 TTS 配置失败：${throwable.message}", StatusTone.Error)
                }
            }
        }
    }

    @Deprecated("Uses the platform document picker without adding another dependency")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_TTS_CONFIG || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        showStatus("正在导入 TTS 配置")
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
                    showStatus("TTS 配置导入成功", StatusTone.Success)
                    appendLog("TTS 配置导入成功，未记录凭据内容")
                }.onFailure { throwable ->
                    showStatus("TTS 配置导入失败：${throwable.message}", StatusTone.Error)
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
            showStatus("麦克风或通知权限被拒绝，语音服务未启动", StatusTone.Error)
        }
        pendingStartAfterPermission = false
        pendingModeAfterPermission = VoiceRuntimeMode.PreviewMock
    }

    private fun stopVoiceService() {
        stopService(Intent(this, VoiceForegroundService::class.java))
        appendLog("已发送停止服务命令")
        showStatus("语音服务已停止")
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

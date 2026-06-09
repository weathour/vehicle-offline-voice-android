package com.company.vehiclevoice

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import com.company.vehiclevoice.update.GitHubReleaseUpdateClient
import com.company.vehiclevoice.update.GitHubReleaseVersion
import com.company.vehiclevoice.update.UpdateApkProvider
import kotlin.concurrent.thread

class MainActivity : Activity() {
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
    private lateinit var updatePanel: TextView
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
        appendLog("当前阶段：上车测试准备；填写车辆/电脑 Redis IP 和端口后，可先连接测试，再启动真实语音。")
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
            text = "Vehicle Offline Voice"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
        }
        root.addView(title)
        root.addView(TextView(this).apply {
            text = "上车流程：连接车辆网络 → 填 Redis IP/端口 → 测试连接 → 启动车上语音测试"
            textSize = 13f
        })
        root.addView(buildRedisConfigPanel())

        root.addView(Button(this).apply {
            text = "1. 测试 Redis 连接 / 解码"
            setOnClickListener { testRedisConnection() }
        })

        root.addView(Button(this).apply {
            text = "2. 启动车上语音测试"
            setOnClickListener {
                remoteRedisCheckBox.isChecked = true
                saveRedisConfig()
                startVoiceServiceWhenPermissionsReady(VoiceRuntimeMode.RealMicManual)
            }
        })

        root.addView(Button(this).apply {
            text = "停止语音服务"
            setOnClickListener { stopVoiceService() }
        })

        connectionPanel = debugLine("连接诊断", "未测试")
        root.addView(connectionPanel)
        root.addView(buildDebugPanel())
        root.addView(buildDeveloperPanel())

        val logTitle = TextView(this).apply {
            text = "现场日志"
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
            dp(220)
        ))

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
            text = "读取外部 Redis（上车测试请保持勾选）"
            isChecked = true
            visibility = View.GONE
        }
        panel.addView(remoteRedisCheckBox)
        panel.addView(TextView(this).apply {
            text = "数据源：外部 Redis（开发模拟入口在下方折叠区）"
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
            setText(loadString(PREF_REDIS_PASSWORD, ""))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        panel.addView(redisPasswordInput)
        panel.addView(TextView(this).apply {
            text = "手机必须与车辆 Redis 在同一网络。连接测试通过后，再启动语音测试。"
            textSize = 12f
        })
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
                connectionPanel.text = "连接诊断：未测试"
                updatePanel.text = "版本更新：未检查"
            }
        })
        developerPanel.addView(Button(this).apply {
            text = "检查 GitHub 版本列表"
            setOnClickListener { checkGitHubVersionList() }
        })
        updatePanel = debugLine("版本更新", "未检查")
        developerPanel.addView(updatePanel)
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

    private fun testRedisConnection() {
        remoteRedisCheckBox.isChecked = true
        val config = selectedVehicleSourceConfig()
        saveRedisConfig()
        connectionPanel.text = "连接诊断：正在测试 ${config.host}:${config.port}/db${config.database} ..."
        appendLog("开始连接测试：${config.displayName}")
        thread(name = "vehicle-redis-connection-test") {
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
                    val message = "连接诊断：connected=${snapshot.diagnostics.connected}，decoded=$decoded，missing=$missing，decodeError=$errors；$basic"
                    connectionPanel.text = message
                    vehiclePanel.text = "只读车况：${snapshot.diagnostics.detail} decoded=$decoded missing=$missing error=$errors"
                    cooperationPanel.text = "协作信息：${snapshot.cooperativeState?.summary ?: "未读取"}"
                    appendLog(message)
                    if (errors > 0 || missing > 0) {
                        appendLog("异常 key：${snapshot.keyStatuses.values.filter { !it.decoded }.take(8).joinToString { "${it.key}:${it.error}" }}")
                    }
                }.onFailure { throwable ->
                    val message = "连接诊断：失败 ${throwable.message ?: throwable::class.java.simpleName}"
                    connectionPanel.text = message
                    statusPanel.text = "状态：Redis 连接失败"
                    appendLog(message)
                }
            }
        }
    }

    private fun checkGitHubVersionList() {
        updatePanel.text = "版本更新：正在读取 GitHub Releases ..."
        appendLog("开始检查 GitHub 版本列表")
        thread(name = "vehicle-github-release-list") {
            val result = runCatching { GitHubReleaseUpdateClient(this).fetchReleases() }
            runOnUiThread {
                result.onSuccess { releases ->
                    if (releases.isEmpty()) {
                        updatePanel.text = "版本更新：没有找到带 APK 的 GitHub release"
                        appendLog("GitHub 版本列表为空或没有 APK 资源")
                    } else {
                        updatePanel.text = "版本更新：找到 ${releases.size} 个可安装版本"
                        showReleaseSelector(releases)
                    }
                }.onFailure { throwable ->
                    val message = throwable.message ?: throwable::class.java.simpleName
                    updatePanel.text = "版本更新：检查失败 $message"
                    appendLog("GitHub 版本检查失败：$message")
                }
            }
        }
    }

    private fun showReleaseSelector(releases: List<GitHubReleaseVersion>) {
        val labels = releases.map { release -> release.title() }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择要安装的版本")
            .setItems(labels) { _, which ->
                downloadSelectedRelease(releases[which])
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun downloadSelectedRelease(release: GitHubReleaseVersion) {
        val apkUrl = release.apkAssetUrl()
        if (apkUrl == null) {
            updatePanel.text = "版本更新：${release.tagName} 没有 APK 资源"
            return
        }
        updatePanel.text = "版本更新：正在下载 ${release.tagName} ..."
        appendLog("开始下载版本：${release.tagName}")
        thread(name = "vehicle-github-apk-download") {
            val result = runCatching {
                GitHubReleaseUpdateClient(this).downloadApk(apkUrl)
            }
            runOnUiThread {
                result.onSuccess { apkFile ->
                    updatePanel.text = "版本更新：${release.tagName} 下载完成，准备安装"
                    installDownloadedApk(apkFile.name)
                }.onFailure { throwable ->
                    val message = throwable.message ?: throwable::class.java.simpleName
                    updatePanel.text = "版本更新：下载失败 $message"
                    appendLog("版本 ${release.tagName} 下载失败：$message")
                }
            }
        }
    }

    private fun installDownloadedApk(fileName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            updatePanel.text = "版本更新：请允许本应用安装未知来源应用，授权后重新选择版本"
            appendLog("安装暂停：需要允许本应用安装未知来源应用")
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        val uri = UpdateApkProvider.contentUri(this, fileName)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, GitHubReleaseUpdateClient.APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching {
            startActivity(intent)
            updatePanel.text = "版本更新：已打开系统安装器"
            appendLog("已打开系统安装器：$fileName")
        }.onFailure { throwable ->
            updatePanel.text = "版本更新：无法打开安装器 ${throwable.message ?: throwable::class.java.simpleName}"
        }
    }

    private fun saveRedisConfig() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(PREF_REDIS_HOST, redisHostInput.text.toString().trim())
            .putString(PREF_REDIS_PORT, redisPortInput.text.toString().trim())
            .putString(PREF_REDIS_DB, redisDbInput.text.toString().trim())
            .putString(PREF_REDIS_PASSWORD, redisPasswordInput.text.toString())
            .apply()
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

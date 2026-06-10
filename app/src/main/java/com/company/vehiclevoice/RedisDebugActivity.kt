package com.company.vehiclevoice

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.company.vehiclevoice.data.readonly.RedisDebugRow
import com.company.vehiclevoice.data.readonly.RedisDebugRows
import com.company.vehiclevoice.data.readonly.RedisVehicleSnapshotProvider
import com.company.vehiclevoice.data.readonly.SocketRedisBinaryDataSource
import com.company.vehiclevoice.data.readonly.SocketRedisConfig
import com.company.vehiclevoice.data.readonly.VehicleDataSourceRuntimeConfig
import kotlin.concurrent.thread

class RedisDebugActivity : Activity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var endpointPanel: TextView
    private lateinit var summaryPanel: TextView
    private lateinit var rowsContainer: LinearLayout
    private lateinit var keyRowsContainer: LinearLayout
    private var running = false
    private var refreshInFlight = false
    private var refreshCount = 0

    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshOnce()
            if (running) mainHandler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContentView())
    }

    override fun onResume() {
        super.onResume()
        running = true
        refreshOnce()
        mainHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS)
    }

    override fun onPause() {
        running = false
        mainHandler.removeCallbacks(refreshRunnable)
        super.onPause()
    }

    private fun buildContentView(): ScrollView {
        val page = ScrollView(this).apply { isFillViewport = true }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(24))
            setBackgroundColor(Color.rgb(248, 250, 252))
        }

        root.addView(TextView(this).apply {
            text = "Redis 调试页面"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(15, 23, 42))
        })
        root.addView(TextView(this).apply {
            text = "1Hz 自动刷新：对比语音模块应读信息、Redis key 可读状态和当前解码内容。"
            textSize = 13f
            setTextColor(Color.rgb(51, 65, 85))
            setPadding(0, dp(4), 0, dp(12))
        })

        endpointPanel = panelText("数据源：未读取")
        summaryPanel = panelText("刷新状态：等待首次读取")
        root.addView(endpointPanel)
        root.addView(summaryPanel)

        root.addView(Button(this).apply {
            text = "立即刷新"
            setOnClickListener { refreshOnce(force = true) }
        })
        root.addView(Button(this).apply {
            text = "返回主页面"
            setOnClickListener { finish() }
        })

        root.addView(sectionTitle("语音模块应读信息对比"))
        rowsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(rowsContainer)

        root.addView(sectionTitle("Redis key 读取明细"))
        keyRowsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(keyRowsContainer)

        page.addView(root)
        return page
    }

    private fun panelText(initial: String): TextView = TextView(this).apply {
        text = initial
        textSize = 14f
        setTextColor(Color.rgb(15, 23, 42))
        setTextIsSelectable(true)
        setPadding(dp(12), dp(8), dp(12), dp(8))
        setBackgroundColor(Color.WHITE)
    }

    private fun sectionTitle(title: String): TextView = TextView(this).apply {
        text = title
        textSize = 17f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.rgb(15, 23, 42))
        setPadding(0, dp(18), 0, dp(8))
    }

    private fun refreshOnce(force: Boolean = false) {
        if (refreshInFlight && !force) return
        refreshInFlight = true
        val config = redisConfigFromPrefs()
        endpointPanel.text = "数据源：${config.host}:${config.port}/db${config.database}，刷新频率 1Hz"
        summaryPanel.text = "刷新状态：第 ${refreshCount + 1} 次读取中 ..."

        thread(name = "redis-debug-refresh") {
            val result = runCatching {
                RedisVehicleSnapshotProvider(
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
                ).readSnapshot()
            }
            runOnUiThread {
                refreshInFlight = false
                refreshCount += 1
                result.onSuccess { snapshot ->
                    val now = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA).format(java.util.Date())
                    endpointPanel.text = "数据源：${snapshot.sourceName}，${snapshot.diagnostics.detail}"
                    summaryPanel.text = "刷新状态：$now，第 $refreshCount 次，${RedisDebugRows.summary(snapshot)}"
                    renderRows(rowsContainer, RedisDebugRows.voiceInfoRows(snapshot))
                    renderRows(keyRowsContainer, RedisDebugRows.redisKeyRows(snapshot))
                }.onFailure { throwable ->
                    val message = throwable.message ?: throwable::class.java.simpleName
                    summaryPanel.text = "刷新状态：第 $refreshCount 次失败，$message"
                    rowsContainer.removeAllViews()
                    rowsContainer.addView(errorRow("Redis 整体连接", message))
                    keyRowsContainer.removeAllViews()
                }
            }
        }
    }

    private fun renderRows(container: LinearLayout, rows: List<RedisDebugRow>) {
        container.removeAllViews()
        rows.forEach { row -> container.addView(debugRow(row)) }
    }

    private fun debugRow(row: RedisDebugRow): LinearLayout {
        val ok = row.readableStatus.contains("全部可读") || row.readableStatus.contains("已读到 / 已解码")
        val partial = row.readableStatus.contains("部分可读")
        val bg = when {
            ok -> Color.rgb(240, 253, 244)
            partial -> Color.rgb(255, 251, 235)
            else -> Color.rgb(254, 242, 242)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(9))
            setBackgroundColor(bg)
            addView(TextView(this@RedisDebugActivity).apply {
                text = "应读：${row.expectedInfo}    key：${row.keyText}"
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.rgb(15, 23, 42))
                setTextIsSelectable(true)
            })
            addView(TextView(this@RedisDebugActivity).apply {
                text = "状态：${row.readableStatus}"
                textSize = 13f
                setTextColor(Color.rgb(30, 64, 175))
                setTextIsSelectable(true)
            })
            addView(TextView(this@RedisDebugActivity).apply {
                text = "内容：${row.readableContent}"
                textSize = 13f
                setTextColor(Color.rgb(51, 65, 85))
                setTextIsSelectable(true)
            })
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(6)) }
        }
    }

    private fun errorRow(label: String, message: String): LinearLayout = debugRow(
        RedisDebugRow(
            expectedInfo = label,
            redisKeys = emptyList(),
            readableStatus = "不可读",
            readableContent = message
        )
    )

    private fun redisConfigFromPrefs(): VehicleDataSourceRuntimeConfig {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val host = prefs.getString(PREF_REDIS_HOST, VehicleDataSourceRuntimeConfig.DEFAULT_REMOTE_HOST)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: VehicleDataSourceRuntimeConfig.DEFAULT_REMOTE_HOST
        val port = prefs.getString(PREF_REDIS_PORT, VehicleDataSourceRuntimeConfig.DEFAULT_REMOTE_PORT.toString())
            ?.trim()
            ?.toIntOrNull()
            ?: VehicleDataSourceRuntimeConfig.DEFAULT_REMOTE_PORT
        val database = prefs.getString(PREF_REDIS_DB, "0")?.trim()?.toIntOrNull() ?: 0
        val password = prefs.getString(PREF_REDIS_PASSWORD, "")?.takeIf { it.isNotBlank() }
        return VehicleDataSourceRuntimeConfig(
            host = host,
            port = port,
            password = password,
            database = database,
            timeoutMs = VehicleDataSourceRuntimeConfig.DEFAULT_TIMEOUT_MS
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REFRESH_INTERVAL_MS = 1_000L
        private const val PREFS = "vehicle_voice_prefs"
        private const val PREF_REDIS_HOST = "redis_host"
        private const val PREF_REDIS_PORT = "redis_port"
        private const val PREF_REDIS_DB = "redis_db"
        private const val PREF_REDIS_PASSWORD = "redis_password"
    }
}

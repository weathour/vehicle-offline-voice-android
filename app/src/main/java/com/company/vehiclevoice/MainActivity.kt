package com.company.vehiclevoice

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var logView: TextView
    private var pendingStartAfterPermission = false
    private val serviceLogListener: (String) -> Unit = { line ->
        runOnUiThread { logView.append("$line\n") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContentView())
        appendLog("项目骨架已启动：VehicleOfflineVoice")
        appendLog("当前阶段：上机前本地 Mock 链路；真实 KWS/ASR 模型与真机安装仍放在最后阶段。")
        appendLog("提示：点击启动后，服务端 KWS/VAD/ASR/NLU/TTS/Unity JSON 会显示在这里。")
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
        }
        root.addView(title)

        root.addView(Button(this).apply {
            text = "启动语音服务"
            setOnClickListener {
                startVoiceServiceWhenPermissionsReady()
            }
        })

        root.addView(Button(this).apply {
            text = "停止语音服务"
            setOnClickListener { stopVoiceService() }
        })

        root.addView(Button(this).apply {
            text = "清空日志"
            setOnClickListener {
                logView.text = ""
                UiLogBus.clear()
            }
        })

        val scrollView = ScrollView(this)
        logView = TextView(this).apply {
            textSize = 14f
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

    private fun startVoiceServiceWhenPermissionsReady() {
        val permissions = missingRuntimePermissions()
        if (permissions.isNotEmpty()) {
            pendingStartAfterPermission = true
            requestPermissions(permissions.toTypedArray(), REQUEST_PERMISSIONS)
            appendLog("已请求运行时权限，授权后再启动服务：${permissions.joinToString()}")
            return
        }
        startVoiceService()
    }

    private fun missingRuntimePermissions(): List<String> {
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

    private fun startVoiceService() {
        val intent = Intent(this, VoiceForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        appendLog("已发送启动前台服务命令")
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
            if (pendingStartAfterPermission) startVoiceService()
        } else {
            appendLog("权限被拒绝，未启动真实麦克风相关服务：${denied.joinToString()}")
        }
        pendingStartAfterPermission = false
    }

    private fun stopVoiceService() {
        stopService(Intent(this, VoiceForegroundService::class.java))
        appendLog("已发送停止服务命令")
    }

    private fun appendLog(message: String) {
        val line = "${System.currentTimeMillis()}  $message\n"
        logView.append(line)
        VoiceLogger.info(message)
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 2001
    }
}

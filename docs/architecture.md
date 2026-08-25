# 渝行智声 Android 架构

## 主链路

```text
VoiceForegroundService
  -> VoicePipelineController
  -> AudioSource -> KeywordSpotter -> VadEngine -> Vosk AsrEngine
  -> IntentParser -> VehicleReadOnlySnapshotProvider
  -> ReplyTemplateEngine
  -> UI log bus（先显示应播报文字）
  -> FallbackTtsEngine
       -> fixed wake WAV
       -> selected provider (Edge / Baidu / Tencent / Android system)
       -> Android system TTS（在线供应商失败时）
       -> fixed unavailable WAV
  -> UnityActionMapper / UnityActionJsonEncoder / UnityEventSink
```

业务层只依赖 `TtsEngine.speak(text)`。`VoicePipeline.speakSafely()` 在调用引擎前发布回答文字，所以合成、网络或播放失败不会让 UI 丢失回答，也不会阻断管线恢复监听。

## TTS 配置

- `TtsProvider` 是界面、前台服务 Intent 和 TTS 工厂共用的四值枚举。
- `TtsConfigStore` 用 Android Keystore 的 AES/GCM 密钥加密供应商配置；应用只保存 IV 和密文。
- 配置只允许固定字段，限制为 32 KB，并校验字段范围和示例占位符。
- 在线响应限制为 10 MB；HTTP、WebSocket 和播放都设置超时。
- 选择在线供应商时，失败后创建 Android 系统 TTS；所有后端失败后播放固定故障提示。

## Android 生命周期

- `MainActivity` 在启动真实麦克风服务前请求 `RECORD_AUDIO` 和 Android 13+ `POST_NOTIFICATIONS`。
- `VoiceForegroundService` 声明并启动 microphone foreground service。
- 播报期间停止 `AudioRecord`；播报结束后重建采集并重置 KWS/VAD，避免回声回灌。
- `VoicePipelineController.start()/stop()/close()` 使用单线程执行器并显式管理状态。

## 安全边界

- Redis 路径在可选 `AUTH`/`SELECT` 后只执行 `GET`；不写车控。
- ASR 音频始终本地处理。只有选用在线 TTS 时，最终回答文字会发给所选供应商。
- API 密钥不写入源码、APK、日志、Release 附件或普通 SharedPreferences。
- 不安全或疑似提示注入的识别文本映射为 `unsafe_rejected`，不修改车辆状态，也不创建 Unity 动作 JSON。
- APK 权限由 `scripts/check_apk_permissions.sh` 检查。

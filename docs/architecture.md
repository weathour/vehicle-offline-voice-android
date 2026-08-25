# 渝行智声 Android 架构

## 范围

本仓库只构建原生 Android/Kotlin APK。车辆数据访问保持只读，Unity 侧仍通过稳定的动作 JSON 边界隔离。

## 本地离线链路

```text
VoiceForegroundService
  -> VoicePipelineController
  -> AudioSource
  -> KeywordSpotter
  -> VadEngine
  -> AsrEngine
  -> IntentParser
  -> VehicleReadOnlySnapshotProvider / VehicleStateStore
  -> ReplyTemplateEngine
  -> FallbackTtsEngine
       -> fixed wake WAV
       -> embedded sherpa-onnx TTS
       -> Android system TTS
       -> fixed unavailable WAV
  -> UnityActionMapper / UnityActionJsonEncoder / UnityEventSink
```

真实麦克风模式在播报期间停止 `AudioRecord`，播报结束后重建采集并重置 KWS/VAD，避免应用自己的声音回灌识别链路。

## 边界规则

- Android API 限制在 Activity、Service、音频和 TTS 适配器内。
- 核心业务模块可在 JVM 上测试；fake/scripted 组件仅用于测试和预览模式。
- 正式麦克风模式使用 Vosk 离线 ASR、规则 NLU、只读 Redis/protobuf 数据源和内置 sherpa-onnx 中文 TTS。
- 业务链路只依赖 `TtsEngine.speak(text)`，不直接依赖 Android `TextToSpeech`。
- Unity 仍由动作 JSON 表示；完成 IPC 接入前不要求 Unity 进程存在。

## Android 生命周期

- `MainActivity` requests `RECORD_AUDIO` and Android 13+ `POST_NOTIFICATIONS` before starting the foreground service.
- `VoiceForegroundService` declares `android:foregroundServiceType="microphone"` and uses microphone FGS type when starting foreground on Android Q+.
- `AndroidAudioRecordSource` refuses to start when `RECORD_AUDIO` is not granted.
- `VoicePipelineController.start()/stop()/close()` use a background single-thread runner, explicit `Idle/Running/Completed/Failed/Stopped/Closed` states, and service cleanup via `close()`.

## 安全与离线约束

- `android.permission.INTERNET` is intentionally present for remote Redis read-only status checks. The Android Redis path issues only `GET` after optional `AUTH`/`SELECT`; simulator write scripts refuse non-loopback writes unless explicitly acknowledged.
- Unsafe/prompt-injection-like ASR text maps to `unsafe_rejected`, does not mutate vehicle state, and does not create Unity action JSON.
- APK permission checks are enforced by `scripts/check_apk_permissions.sh`.
- sherpa-onnx 运行库、中文模型和固定提示音都位于 APK 内；主播报链路不依赖网络、Play 商店或系统 TTS。
- 第三方组件和模型来源见 `THIRD_PARTY_NOTICES.md`。

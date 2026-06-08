# VehicleOfflineVoice

Linux/Codex-first Android Kotlin project for an offline vehicle voice MVP.

Current stage: **real-device offline voice core-loop baseline**. The APK builds locally and now supports a real microphone + local Vosk wake/ASR + VAD + rule NLU + Mock/local vehicle state + Chinese TTS + Unity action JSON debug loop on an ordinary Android phone, while preserving mock/scripted paths for regression.

## Environment

This repository uses a user-local, CLI-first Android development environment:

- JDK 17: `~/.local/opt/jdk-17`
- Android SDK: `~/Android/Sdk`
- Gradle Wrapper: `./gradlew`

Source environment manually if needed:

```bash
source .codex/android-env.sh
```

## Local Codex verification commands

Run these before any phone/device install:

```bash
bash scripts/check_android_env.sh
bash scripts/test_unit.sh
bash scripts/build_debug.sh
bash scripts/lint_debug.sh
bash scripts/package_debug.sh
bash scripts/check_apk_permissions.sh
```

Expected debug APK path:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Implemented local modules

- `audio`: PCM frame model, RMS, fake PCM source, guarded Android `AudioRecord` source.
- `kws`: keyword spotter interface, scripted mock KWS, and local Vosk wake adapter.
- `vad`: energy-based VAD with speech start/end events.
- `asr`: ASR interface, scripted mock ASR, and local Vosk offline ASR adapter.
- `nlu`: Chinese rule intent parser with unsafe/fallback rejection.
- `data`: in-memory Mock Redis-like vehicle state store.
- `template`: Chinese reply template engine.
- `tts`: TTS interface and mock recorder.
- `action`: Unity action model, mapper, manual JSON encoder, event sink.
- `core`: full voice pipeline and lifecycle controller.
- `log`: Android and recording log sinks.

## Android behavior

- `MainActivity` requests microphone and notification permissions before starting the service.
- `VoiceForegroundService` declares and starts with microphone foreground service type.
- Service supports preview/mock, virtual-mic smoke, and real-microphone manual validation modes.
- Real-microphone mode logs KWS/VAD/ASR/NLU/TTS/Unity JSON events and is observable through the in-app debug panel.
- `AndroidAudioRecordSource` refuses to start without `RECORD_AUDIO`.

## Development constraints

- No Unity project coupling in this repository.
- No `INTERNET` permission.
- No cloud ASR/TTS or external Redis.
- Install to phone only after local verification gates pass.
- Replace or tune KWS/ASR/TTS engines behind existing interfaces instead of rewriting business logic.

## Current handoff

The ordinary Android phone smoke test has progressed from mock-chain verification to a real offline voice-loop baseline. Latest observed successful commands include `小车小车 -> 打开空调` and `小车小车 -> 关闭空调`. See:

- `docs/current-real-device-voice-status-2026-06-08.md`
- `docs/device-smoke-result-2026-06-08.md`
- `docs/handoff-next-stage.md`

Next development should focus on **Real-device Voice Robustness and Integration Prep**: command recognition matrix, ASR correction/rule hardening, wake reliability tuning, phone-side debug UX, soak/resource stability, and Unity/RK3588S handoff.

## Final phone-only commands

Only after local verification passes:

```bash
bash scripts/final_install_phone.sh
bash scripts/final_logcat.sh
```

See `docs/final-device-test-checklist.md`.

# VehicleOfflineVoice

Linux/Codex-first Android Kotlin project for an offline vehicle voice MVP.

Current stage: **pre-device local completion**. The APK builds locally and contains a mock/rule offline voice pipeline for 常驻 KWS + VAD + ASR + 规则语义 + Mock Redis + 模板/TTS + Unity action JSON. Real phone installation is intentionally deferred to the final device step.

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
- `kws`: keyword spotter interface and scripted mock KWS.
- `vad`: energy-based VAD with speech start/end events.
- `asr`: ASR interface and scripted mock ASR.
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
- Service currently runs a finite preview mock pipeline and logs KWS/VAD/ASR/NLU/TTS/Unity JSON events.
- `AndroidAudioRecordSource` exists as the real microphone seam and refuses to start without `RECORD_AUDIO`.

## Development constraints

- No Unity project coupling in this repository.
- No `INTERNET` permission.
- No cloud ASR/TTS or external Redis.
- Do not install to phone until final device validation.
- Replace KWS/ASR/TTS engines behind existing interfaces instead of rewriting business logic.

## Current handoff

The ordinary Android phone smoke test has matched the expected pre-device mock-chain behavior. See:

- `docs/device-smoke-result-2026-06-08.md`
- `docs/handoff-next-stage.md`

Next development should start from the documented **Offline Voice Core Loop** stage: real microphone capture, always-on lifecycle hardening, local KWS, VAD endpointing, offline ASR, rule NLU/local state, Chinese reply/TTS, and an end-to-end QA gate while preserving the no-`INTERNET` constraint. See `docs/next-stage-offline-voice-core-goals.md`.

## Final phone-only commands

Only after local verification passes:

```bash
bash scripts/final_install_phone.sh
bash scripts/final_logcat.sh
```

See `docs/final-device-test-checklist.md`.

# Pre-device completion boundary

This document separates the **上机前最终标准** from later real-device/model work.

## Required before phone/device install

- Local CLI Android environment is reproducible.
- APK builds, lints, unit-tests, packages, and passes permission checks.
- No `INTERNET` permission and no network dependency.
- Android service declares microphone foreground service type.
- Permission flow requests microphone/notification before service start.
- Mock/rule offline chain is implemented and tested:
  - fake PCM audio;
  - scripted KWS;
  - energy VAD with pre-roll;
  - scripted ASR;
  - rule NLU with unsafe rejection;
  - in-memory Mock Redis-like store;
  - reply templates and mock TTS;
  - Unity action JSON sink.
- Final phone scripts exist but are not run locally.

## Explicitly not required before phone/device install

- Continuous real microphone KWS loop running for hours.
- Real sherpa-onnx/Vosk/Whisper/RKNN integration.
- Android 14 `targetSdk >= 34` foreground-service migration.
- Unity IPC bridge.
- Real Redis/network connection.
- RK3588S/NPU optimization.

## Why remaining architecture WATCH items are not blockers

The current service deliberately runs a finite preview/mock pipeline on a background controller. That proves the app packaging, lifecycle seam, logging, state/action protocol, and offline safety policy without performing true microphone/device validation. Real always-on microphone behavior is a later device/model phase and must be tested only after final phone install is allowed.

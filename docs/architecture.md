# Pre-device architecture

## Scope

This repository is a native Android/Kotlin APK only. It intentionally does not read, depend on, or modify the parent Unity project before the final integration stage.

## Local offline pipeline

```text
VoiceForegroundService
  -> VoicePipelineController
  -> AudioSource
  -> KeywordSpotter
  -> VadEngine
  -> AsrEngine
  -> IntentParser
  -> VehicleStateStore (Mock Redis)
  -> ReplyTemplateEngine / TtsEngine
  -> UnityActionMapper / UnityActionJsonEncoder / UnityEventSink
```

## Boundary rules

- Android-specific APIs are limited to Activity/Service and Android adapters such as `AndroidAudioRecordSource`.
- Core business modules are JVM-testable and covered by unit tests.
- The pre-device build uses fake/scripted KWS and ASR. Real offline engines can replace `KeywordSpotter` and `AsrEngine` without changing NLU/action/store code.
- `MockRedisStore` is an in-memory Redis-like interface implementation; it performs no network I/O.
- Unity is represented by stable action JSON only; no Unity process or IPC is required before final integration.

## Android lifecycle hardening

- `MainActivity` requests `RECORD_AUDIO` and Android 13+ `POST_NOTIFICATIONS` before starting the foreground service.
- `VoiceForegroundService` declares `android:foregroundServiceType="microphone"` and uses microphone FGS type when starting foreground on Android Q+.
- `AndroidAudioRecordSource` refuses to start when `RECORD_AUDIO` is not granted.
- `VoicePipelineController.start()/stop()/close()` use a background single-thread runner, explicit `Idle/Running/Completed/Failed/Stopped/Closed` states, and service cleanup via `close()`.

## Security/offline constraints

- `android.permission.INTERNET` is intentionally present for remote Redis read-only status checks. The Android Redis path issues only `GET` after optional `AUTH`/`SELECT`; simulator write scripts refuse non-loopback writes unless explicitly acknowledged.
- Unsafe/prompt-injection-like ASR text maps to `unsafe_rejected`, does not mutate vehicle state, and does not create Unity action JSON.
- APK permission checks are enforced by `scripts/check_apk_permissions.sh`.


## Current WATCH items before real engine/device phase

- The service currently runs a finite preview/mock pipeline; real always-on microphone mode must keep using the background controller and wire a cancellable real `AudioSource`.
- Android 14+ microphone foreground-service permission requirements are declared and covered by the APK permission check.
- `MockRedisStore` intentionally implements only in-memory key/value state needed by this MVP; it is not a network Redis client.

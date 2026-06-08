# Offline core loop local QA result

Date: 2026-06-08
Stage: G8 Offline core loop end-to-end QA gate

## Local virtual-microphone gate

The local gate uses deterministic TTS-like generated PCM as a virtual microphone source. The tested path is:

```text
VirtualTtsPcmSource
  -> VirtualPcmKeywordSpotter
  -> EnergyVadEngine
  -> VirtualPcmCommandAsrEngine
  -> RuleIntentParser
  -> MockRedisStore / VehicleStateProjector
  -> ReplyTemplateEngine / TtsEngine
  -> UnityActionJsonEncoder / RecordingUnityEventSink
```

The virtual path intentionally proves pipeline wiring and state/action semantics. It does not claim production acoustic accuracy; production KWS/ASR is represented by real Vosk adapter classes and packaged local model assets.

## Command coverage

Covered in JVM tests:

- `打开空调` -> `air_conditioner_on`
- `关闭空调` -> `air_conditioner_off`
- `打开车窗` -> `window_open`
- `关闭车窗` -> `window_close`
- `调高温度` -> `temperature_up`
- `调低温度` -> `temperature_down`

## Required full local gates

Run before any final physical microphone validation:

```bash
bash scripts/check_android_env.sh
bash scripts/test_unit.sh
bash scripts/build_debug.sh
bash scripts/lint_debug.sh
bash scripts/package_debug.sh
bash scripts/check_apk_permissions.sh
```

## Final physical microphone validation checklist

Physical real microphone validation is intentionally last and manual, per the execution constraint.

Record a new `docs/physical-mic-validation-<date>.md` containing:

1. APK install result.
2. Runtime permission grant and denial behavior.
3. Real microphone RMS/frame activity in UI/logcat.
4. Repeated wake attempts and observed wake logs.
5. VAD `speech_start` / `speech_end` behavior.
6. ASR command table for the initial six commands.
7. Chinese reply/TTS result.
8. Repeated start/stop result.
9. Short soak result.
10. Confirmation that merged APK still has no `INTERNET` permission.

## Remaining after this local gate

- Physical microphone evidence on ordinary Android phone.
- Production wake-word robustness tuning.
- RK3588S migration and latency/CPU/RAM measurements.
- Unity bridge.

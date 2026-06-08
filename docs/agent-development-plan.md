# Agent-first local development plan

## Local-only milestones

- [x] M1: Android project skeleton + MainActivity + VoiceForegroundService.
- [x] M2: AudioSource abstraction + FakePcmSource + guarded AndroidAudioRecordSource.
- [x] M3: KwsEngine/KeywordSpotter interface + scripted mock KWS.
- [x] M4: VAD interface + energy VAD.
- [x] M5: ASR interface + scripted mock ASR.
- [x] M6: RuleIntentParser + MockRedisStore + state projector.
- [x] M7: ReplyTemplateEngine + mock TTS boundary.
- [x] M8: Unity action JSON + event sink.
- [x] M9: VoicePipeline full fake-chain unit tests.
- [x] M10: Debug APK package and APK permission guard.

## Required local gates

```bash
bash scripts/check_android_env.sh
bash scripts/test_unit.sh
bash scripts/build_debug.sh
bash scripts/lint_debug.sh
bash scripts/package_debug.sh
bash scripts/check_apk_permissions.sh
```

## Final phone-only validation

1. Install APK with `bash scripts/final_install_phone.sh`.
2. Grant microphone/notification permissions.
3. Start foreground service.
4. Confirm mock preview pipeline logs KWS/VAD/ASR/NLU/TTS/Unity JSON.
5. Capture `adb logcat -s VehicleVoice`.
6. After real KWS/VAD/ASR integration, run the real 8 command tests.

## Deferred after pre-device phase

- Real offline KWS/ASR/TTS model integration.
- Unity IPC bridge.
- Real Redis or vehicle bus connection.
- RK3588S/RKNN/NPU optimization.

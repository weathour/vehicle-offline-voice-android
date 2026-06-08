# Handoff: pre-device APK verified, next-stage plan

Date: 2026-06-08
Project: `VehicleOfflineVoice`
Path: `/home/weathour/document/programs/chongqingUNITY/android-apk`

## 1. Current handoff status

The current Android APK is an independent native Kotlin project. It does not depend on the parent Unity project.

### Verified by local gates

The following local gates have passed:

```bash
bash scripts/check_android_env.sh
bash scripts/test_unit.sh
bash scripts/build_debug.sh
bash scripts/lint_debug.sh
bash scripts/package_debug.sh
bash scripts/check_apk_permissions.sh
```

APK path:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Permission policy:

- Includes `RECORD_AUDIO`.
- Includes `FOREGROUND_SERVICE`.
- Includes `POST_NOTIFICATIONS`.
- Does **not** include `INTERNET`.
- `VoiceForegroundService` declares `android:foregroundServiceType="microphone"`.

### Verified on ordinary Android phone

User-side smoke test matched expectation:

- APK installed successfully.
- App opened successfully.
- Runtime permission popups appeared normally.
- `启动语音服务` / `停止语音服务` buttons responded.
- After UI log bridge update, service-side mock pipeline logs are visible in the app page.
- The current mock pipeline demonstrates: KWS -> VAD -> ASR -> NLU -> Mock Redis -> reply/TTS -> Unity action JSON.

## 2. What the current APK can do

The current build is a **pre-device/mock-chain verification APK**.

It can verify:

1. Android packaging and installability.
2. Runtime permission flow.
3. Foreground service start/stop and notification behavior.
4. Service-to-UI logging.
5. Full local mock voice chain:
   - fake PCM audio;
   - scripted wake word event;
   - energy VAD with pre-roll;
   - scripted ASR text `打开空调`;
   - rule NLU intent `air_conditioner_on`;
   - in-memory Mock Redis state mutation;
   - reply `已为你打开空调`;
   - mock TTS recording;
   - Unity action JSON emission.
6. Offline safety policy:
   - no network permission;
   - unsafe/prompt-injection-like text is rejected before action mapping;
   - fallback/unsafe commands do not mutate state or emit Unity action JSON.

## 3. What it intentionally does not do yet

The following are not bugs in this handoff; they are next-stage work:

- It does not truly listen for spoken wake words.
- It does not run real ASR on microphone input.
- It does not play real TTS audio.
- It does not connect to Unity.
- It does not connect to real Redis.
- It does not use RK3588S NPU/RKNN.
- It does not require `INTERNET` and must not add it for the offline MVP.

## 4. Recommended next-stage objective

The next phase should move from **mock-chain proof** to **real local microphone pipeline proof**, still without cloud/network dependency.

Recommended objective:

> On a normal Android phone first, replace the finite fake PCM preview with a controlled real `AudioRecord` capture mode, keep scripted/mock KWS and ASR initially, verify continuous foreground-service microphone lifecycle, VAD events from real audio, UI/log observability, and safe start/stop behavior. Only after this is stable, integrate a real offline KWS/ASR engine.

## 5. Suggested next-stage milestones

### M11: Real microphone capture smoke mode

Goal:

- Add a selectable runtime mode: `Preview Mock` vs `Real Mic Smoke`.
- Wire `AndroidAudioRecordSource` into the service behind the existing `AudioSource` interface.
- Keep KWS/ASR scripted at first.
- Use real mic frames only to prove audio capture and VAD behavior.

Acceptance:

- Start service with granted `RECORD_AUDIO`.
- UI logs show real frame RMS values.
- VAD logs speech/silence transitions when the user speaks or is silent.
- Stop button releases `AudioRecord` without crash or stuck foreground service.
- No `INTERNET` permission.

### M12: Real KWS candidate integration

Goal:

- Select and integrate one offline wake-word path.
- Keep it behind `KeywordSpotter`.

Candidates:

- sherpa-onnx keyword spotting;
- Porcupine only if license/offline deployment is acceptable;
- custom small ONNX/RKNN path later for RK3588S.

Acceptance:

- False wake rate and wake latency can be logged.
- Wake event enters the existing VAD/ASR session state.
- The app still builds and runs without network permission.

### M13: Real offline ASR integration

Goal:

- Replace `ScriptedAsrEngine` with an offline ASR adapter behind `AsrEngine`.

Candidates:

- sherpa-onnx Android offline ASR;
- Vosk Android;
- whisper.cpp only if device latency is acceptable.

Acceptance:

- Real spoken commands such as `打开空调`, `关闭空调`, `打开车窗` parse into the existing `RuleIntentParser`.
- ASR timeout/fallback behavior is explicit.
- The mock ASR remains available for regression tests.

### M14: Android TTS or offline TTS

Goal:

- Replace or supplement `MockTtsEngine` with a real TTS adapter.

Acceptance:

- Reply text is spoken or intentionally logged when TTS is unavailable.
- TTS failure does not block Unity action generation.

### M15: Unity action bridge

Goal:

- Replace `RecordingUnityEventSink` with a real Unity IPC bridge.

Possible bridges:

- local socket on device;
- Android broadcast/intent bridge;
- file/queue bridge if Unity app can poll;
- direct integration only when Unity project boundary is reopened.

Acceptance:

- Existing Unity action JSON schema remains stable.
- Unity receives `air_conditioner_on` and can log/render it.
- Voice pipeline remains testable without Unity via `RecordingUnityEventSink`.

### M16: RK3588S migration

Goal:

- Move from ordinary Android phone to the RK3588S Android 13 target.

Acceptance:

- APK installs.
- Foreground service and microphone permission behavior works.
- Real KWS/ASR latency and CPU/RAM usage are measured.
- NPU/RKNN optimization is considered only after baseline CPU path is measured.

## 6. Engineering rules for the next agent

- Keep the current project independent from the parent Unity project unless explicitly told otherwise.
- Do not add `INTERNET` permission for offline MVP work.
- Keep fake/mock implementations available for unit tests.
- Add real engines as adapters behind existing interfaces:
  - `AudioSource`
  - `KeywordSpotter`
  - `AsrEngine`
  - `TtsEngine`
  - `UnityEventSink`
- Do not put Android APIs into pure core business modules unless there is a clear adapter boundary.
- Run local gates before every device install.
- Device install is allowed only for explicit phone/device validation steps.

## 7. Fast resume checklist

Before continuing development:

```bash
source .codex/android-env.sh
bash scripts/check_android_env.sh
bash scripts/test_unit.sh
bash scripts/build_debug.sh
bash scripts/lint_debug.sh
bash scripts/package_debug.sh
bash scripts/check_apk_permissions.sh
```

Then, for device validation only:

```bash
bash scripts/final_install_phone.sh
bash scripts/final_logcat.sh
```

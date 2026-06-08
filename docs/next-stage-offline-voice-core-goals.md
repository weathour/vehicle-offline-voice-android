# Next stage: offline voice core loop goals

Date: 2026-06-08
Project: `VehicleOfflineVoice`
Stage name: **离线语音核心闭环 / Offline Voice Core Loop**

## 1. Stage purpose

The repository has completed the pre-device/mock-chain baseline. The next large stage should turn the APK from a finite mock preview into a real local voice core loop on an ordinary Android phone first:

```text
常驻服务
  -> 真实麦克风采集
  -> 本地 KWS 唤醒
  -> VAD 端点检测
  -> 离线 ASR
  -> 规则语义解析
  -> Mock/本地车辆状态
  -> 中文回复 / TTS
  -> Unity action JSON 日志输出
```

This stage is still **not** the Unity integration stage, RK3588S migration stage, or real vehicle-control stage.

## 2. Recommended goal count

Recommended split: **8 goals**.

Reason:

- Fewer than 6 goals would merge too many independently failing concerns, especially KWS, ASR, and long-running service lifecycle.
- More than 10 goals would create unnecessary management overhead for this repository size.
- 8 goals keeps each goal independently testable while preserving a clear end-to-end stage gate.

## 3. Goals

### G1: Runtime mode and real microphone capture smoke

Purpose:

- Add a selectable mode so the current mock preview remains available while a real microphone smoke mode can be tested.
- Prove `AndroidAudioRecordSource` works in the service path before introducing real KWS/ASR models.

Scope:

- Add runtime modes such as `Preview Mock` and `Real Mic Smoke`.
- Wire `AndroidAudioRecordSource` through the existing `AudioSource` boundary.
- Log frame sequence, RMS, and capture lifecycle events.
- Keep KWS and ASR scripted during the first microphone smoke pass.

Acceptance criteria:

- Starting service with `RECORD_AUDIO` granted opens the microphone.
- UI/logcat show live RMS or frame activity from real input.
- Stop releases `AudioRecord` without crash or stuck foreground service.
- Existing mock preview mode still works.
- APK still has no `INTERNET` permission.

Verification:

```bash
bash scripts/test_unit.sh
bash scripts/build_debug.sh
bash scripts/lint_debug.sh
bash scripts/check_apk_permissions.sh
```

Device smoke:

- Start real mic smoke mode.
- Speak and stay silent; confirm RMS changes are visible.
- Stop/start repeatedly.

---

### G2: Always-on foreground service lifecycle hardening

Purpose:

- Make the service robust enough for continuous local listening experiments.

Scope:

- Ensure service start/stop/restart behavior is deterministic.
- Handle permission denial and microphone initialization failure safely.
- Make controller cancellation and resource release observable.
- Add a clear state log: idle, listening, wake detected, recording utterance, recognizing, replying, stopped/failed.

Acceptance criteria:

- Repeated start/stop does not create duplicate pipeline threads.
- Permission denial does not crash the app.
- Service can run for a bounded soak test, initially 30 minutes, then 60 minutes.
- Stop button releases microphone and foreground notification.

Verification:

- Unit tests for controller state transitions where possible.
- Device smoke for repeated start/stop and short soak.
- Log review for resource release.

---

### G3: Local KWS adapter integration

Purpose:

- Replace scripted wake events with a real local wake-word path behind `KeywordSpotter`.

Scope:

- Select one offline KWS implementation for Android.
- Preferred first candidate: sherpa-onnx KWS, if model size, packaging, license, and Android support are acceptable.
- Keep `ScriptedKeywordSpotter` for tests and regression.
- Add wake latency/confidence logging.

Acceptance criteria:

- Wake word can be detected without network permission.
- Wake event enters the existing VAD/utterance session path.
- False wake and missed wake observations can be logged.
- Mock/scripted KWS remains available for JVM tests.

Verification:

- Unit tests for adapter-independent KWS event behavior.
- Device smoke with repeated wake phrase attempts.
- Permission check confirms no `INTERNET`.

---

### G4: Real-input VAD session and endpointing

Purpose:

- Turn the current energy VAD into a practical utterance boundary component for real microphone input.

Scope:

- Tune RMS thresholds and silence duration for real input.
- Preserve pre-roll behavior.
- Add utterance timeout and max-frame guard.
- Ensure the pipeline returns to wake/listening state after an utterance.

Acceptance criteria:

- Silence does not constantly trigger speech.
- Speaking after wake triggers speech start.
- Stopping speech triggers speech end within a reasonable delay.
- Timeout/fallback behavior is explicit.
- VAD parameters can be changed without rewriting the pipeline.

Verification:

- Existing VAD unit tests remain passing.
- Add tests for timeout/max-frame behavior if new logic is introduced.
- Device smoke: silence, short command, noisy background.

---

### G5: Offline ASR adapter integration

Purpose:

- Replace `ScriptedAsrEngine` with a real offline ASR path behind `AsrEngine`.

Scope:

- Select and integrate one offline ASR candidate.
- Candidate order for first evaluation:
  1. sherpa-onnx Android offline ASR;
  2. Vosk Android;
  3. whisper.cpp only if latency and packaging are acceptable.
- Keep `ScriptedAsrEngine` for tests and deterministic regression.
- Add ASR timeout, low-confidence, and empty-result handling.

Acceptance criteria:

- No cloud/network dependency.
- Spoken commands can be recognized well enough for the rule parser:
  - `打开空调`
  - `关闭空调`
  - `打开车窗`
  - `关闭车窗`
  - `调高温度`
  - `调低温度`
- Failed/uncertain ASR returns fallback and does not mutate state.

Verification:

- Unit tests for `AsrEngine` contract and fallback behavior.
- Device command test set with repeated attempts.
- Build/package/permission gates.

---

### G6: Rule NLU and local vehicle-state command coverage

Purpose:

- Expand the rule parser and Mock/local state into a stable vehicle-command core.

Scope:

- Extend command grammar and synonyms for the initial command set.
- Define a small local vehicle-state schema.
- Keep unsafe/prompt-injection-like text rejected before state mutation or action generation.
- Keep state mutation deterministic and testable.

Acceptance criteria:

- Valid commands update local state correctly.
- Unknown commands produce fallback.
- Unsafe commands produce unsafe rejection.
- Fallback/unsafe commands do not emit Unity action JSON and do not mutate state.
- State snapshot is visible in logs or UI during testing.

Verification:

- Unit tests for each command family.
- Unit tests for unsafe/fallback rejection.
- End-to-end tests with scripted ASR and mock components.

---

### G7: Chinese reply and TTS adapter

Purpose:

- Replace or supplement mock TTS with audible Chinese reply behavior while keeping a safe fallback.

Scope:

- First integrate Android system TTS if acceptable for the MVP.
- If Android TTS is unavailable or not offline enough for the target, keep log-only fallback and evaluate an offline TTS adapter later.
- Ensure TTS failure does not block state mutation or Unity action generation.

Acceptance criteria:

- Successful commands produce a Chinese reply.
- Reply can be spoken or explicitly logged when speech output is unavailable.
- TTS failures are logged and non-fatal.
- `MockTtsEngine` remains available for tests.

Verification:

- Unit tests for reply template behavior remain passing.
- Device smoke verifies audible or logged reply.
- Failure path test for TTS adapter if feasible.

---

### G8: Offline core loop end-to-end QA gate

Purpose:

- Declare the next large stage complete only after the real local voice loop is demonstrably stable.

Scope:

- Create a repeatable manual test checklist for the offline core loop.
- Add logs for each stage transition.
- Package a debug APK and run permission checks.
- Record device smoke results in docs.

Acceptance criteria:

- End-to-end flow works on ordinary Android phone:

```text
wake phrase -> VAD utterance -> offline ASR -> rule intent -> local state -> Chinese reply/TTS
```

- At least the initial command set is tested.
- No `INTERNET` permission.
- Mock/scripted mode still passes regression tests.
- Known model limitations are documented.

Verification:

```bash
bash scripts/check_android_env.sh
bash scripts/test_unit.sh
bash scripts/build_debug.sh
bash scripts/lint_debug.sh
bash scripts/package_debug.sh
bash scripts/check_apk_permissions.sh
```

Device evidence:

- APK install result.
- Permission flow result.
- Start/stop result.
- Wake/KWS result.
- VAD result.
- ASR command table.
- TTS/reply result.
- Short soak result.

## 4. Work not included in this stage

The following should remain after the offline voice core loop stage:

### R1: Unity action bridge

Replace `RecordingUnityEventSink` with a real Unity communication path while keeping the JSON schema stable.

Possible paths:

- Android broadcast/intent bridge;
- local socket;
- file/queue bridge;
- direct Unity integration only after the Unity project boundary is reopened.

### R2: RK3588S Android 13 migration

Move from ordinary Android phone to the target RK3588S Android device.

Validate:

- APK install;
- microphone permission and foreground service behavior;
- KWS/ASR latency;
- CPU/RAM use;
- long-running stability.

### R3: RKNN/NPU/model optimization

Only after the CPU baseline is measured on RK3588S:

- model size reduction;
- ONNX to RKNN evaluation;
- NPU acceleration;
- latency/power/memory optimization.

### R4: Real vehicle-control/state integration

Replace Mock/local state with real vehicle integration only when the external control boundary is clear.

Possible dependencies:

- vehicle-control API;
- CAN/SOME-IP/serial/vendor SDK;
- real state synchronization;
- safety policy and command confirmation rules.

### R5: Product hardening and release packaging

After the technical loop works:

- UX polish;
- observability and diagnostics;
- fault recovery;
- release build/proguard/signing strategy;
- deployment documentation.

## 5. Suggested execution order

Recommended order:

1. G1 real mic smoke.
2. G2 service lifecycle hardening.
3. G4 VAD on real input.
4. G6 NLU/state command coverage expansion.
5. G3 KWS integration.
6. G5 ASR integration.
7. G7 TTS adapter.
8. G8 end-to-end QA gate.

Rationale:

- Real microphone and lifecycle must be stable before model integration.
- VAD and NLU/state can be strengthened before ASR quality becomes the main blocker.
- KWS and ASR are the highest integration-risk goals and should be isolated behind existing interfaces.
- TTS can be added after command success behavior is stable.
- G8 is a closure gate, not a feature bucket.

## 6. Stage completion definition

This next large stage is complete when:

- ordinary Android phone can run the local voice core loop without network permission;
- real microphone, real KWS, VAD, real offline ASR, rule NLU, local state, and Chinese reply/TTS all work together;
- mock/scripted components remain available for regression tests;
- local verification gates pass;
- device smoke evidence is recorded in docs;
- remaining work is clearly limited to Unity bridge, RK3588S migration, model optimization, real vehicle integration, and product hardening.

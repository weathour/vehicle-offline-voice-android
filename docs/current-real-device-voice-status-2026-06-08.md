# Current real-device voice status and next-stage plan

Date: 2026-06-08
Project: `VehicleOfflineVoice`
Device used in the latest live debug session: ordinary Android phone via USB/ADB
Build: debug APK, package `com.company.vehiclevoice.debug`

## 1. Current status

The project has moved beyond the earlier pre-device mock-chain handoff. The current debug APK now contains a real local offline voice loop on an ordinary Android phone:

```text
真实麦克风
  -> Vosk 本地唤醒/KWS
  -> VAD 端点检测
  -> Vosk 离线 ASR
  -> 规则语义解析
  -> 本地/Mock 车辆状态
  -> 中文 TTS 回复
  -> Unity action JSON 日志输出
```

The app also includes an in-app debug panel for phone-side testing. The panel shows the latest status, wake event, ASR text, intent, TTS reply, and audio/debug information so the user does not need to rely only on desktop logcat while testing on the phone.

## 2. Latest observed effect

The latest real-device logs show that the core loop is working for the air-conditioner commands.

Wake phrase success:

```text
Pipeline state=wake_detected
KWS wake keyword=小车小车
```

Open A/C command success:

```text
ASR text=打开 空调 confidence=0.8
NLU intent=air_conditioner_on reason=matched
TTS reply=已为你打开空调
Unity action json=...air_conditioner_on...
```

Close A/C command success:

```text
ASR text=关闭 空调 confidence=0.8
NLU intent=air_conditioner_off reason=matched
TTS reply=已为你关闭空调
Unity action json=...air_conditioner_off...
```

This confirms the following are currently functioning together on the phone:

1. real microphone capture;
2. local wake detection for `小车小车` and the configured wake-word variants;
3. offline ASR for at least `打开空调` / `关闭空调`;
4. rule intent matching for `air_conditioner_on` and `air_conditioner_off`;
5. Chinese TTS reply;
6. Unity action JSON emission for downstream integration.

## 3. Known limitation from the same session

One fallback case was also observed:

```text
ASR text=速度 怎样 confidence=0.8
NLU intent=fallback reason=no_rule_match
TTS reply=没有识别到有效指令
```

Interpretation:

- The pipeline did not crash or hang.
- The utterance reached ASR, NLU, and TTS normally.
- The fallback was caused by the recognized text not matching a supported rule.
- This is now mainly an ASR robustness / command grammar / synonym-correction problem, not a service-lifecycle or microphone problem.

## 4. Current completion boundary

The current stage can be considered a **real-device offline voice core-loop baseline** rather than a production-ready assistant.

Completed baseline:

- Android build/package/install path is proven.
- No `INTERNET` permission is used.
- Real microphone can drive the voice loop.
- Local KWS and offline ASR can run from packaged model assets.
- The A/C open/close path is confirmed end-to-end.
- TTS reply and action JSON are observable.
- Phone-side debug observability has been added.

Not complete yet:

- stable recognition quality across the full initial command set;
- quantified wake false-positive / false-negative behavior;
- quantified ASR success rate under different speakers, distance, and noise;
- long-running soak evidence;
- real Unity bridge;
- RK3588S target validation;
- real vehicle-control integration.

## 5. Recommended next stage

Recommended stage name: **真机语音鲁棒性与集成准备 / Real-device Voice Robustness and Integration Prep**.

Recommended split: **6 goals**.

Reason:

- The core technical loop is already present, so the next stage should not be another broad feature-build stage.
- The main risks have shifted to recognition quality, repeatability, observability, and integration readiness.
- Six goals keep the work measurable without over-splitting small tuning tasks.

### N1: Command-set recognition matrix

Goal:

- Build a repeatable manual/automated test matrix for the initial supported commands.

Initial commands:

- `打开空调`
- `关闭空调`
- `打开车窗`
- `关闭车窗`
- `调高温度`
- `调低温度`

Acceptance:

- Each command is tested across multiple attempts.
- For every failed attempt, record the raw ASR text.
- The debug panel or docs expose success/failure evidence clearly.

### N2: ASR correction and rule parser hardening

Goal:

- Convert common misrecognitions into safe, deterministic aliases or correction rules.

Acceptance:

- Known harmless variants such as spaced text or common substitutions still map to the intended command.
- Unknown text still falls back safely.
- Unsafe/prompt-injection-like input still cannot mutate vehicle state or emit action JSON.

### N3: Wake-word reliability tuning

Goal:

- Measure and tune wake detection behavior for `小车小车` / `你好车机` variants.

Acceptance:

- Repeated wake attempts produce documented hit/miss counts.
- False wakes during silence/background speech are recorded.
- Wake state returns cleanly after successful or failed command sessions.

### N4: Phone-side debug UX improvement

Goal:

- Improve the in-app debug panel for fast phone testing.

Candidate improvements:

- append a short rolling history instead of only latest values;
- show the current runtime mode and model status;
- show last wake time, last ASR text, last TTS reply, and last action JSON;
- add a visible fallback/unsafe reason.

Acceptance:

- A tester can diagnose wake, ASR, NLU, and TTS behavior on the phone screen without immediately opening logcat.

### N5: Soak and resource stability check

Goal:

- Verify that repeated start/stop and a short always-on listening session remain stable.

Acceptance:

- Repeated start/stop does not create duplicate recognizers or stuck foreground services.
- A bounded listening session runs without crash.
- Stop releases microphone resources.
- No `INTERNET` permission is added.

### N6: Integration handoff for Unity/RK3588S

Goal:

- Prepare the next handoff after phone robustness: Unity bridge and target-device migration.

Acceptance:

- The action JSON schema is documented with examples.
- The recommended Unity bridge path is chosen or narrowed.
- RK3588S validation checklist is updated.
- Remaining real-vehicle integration assumptions are explicitly listed.

## 6. Suggested order

1. N1 command-set recognition matrix.
2. N2 ASR correction and rule parser hardening.
3. N3 wake-word reliability tuning.
4. N4 debug UX improvement.
5. N5 soak/resource stability check.
6. N6 Unity/RK3588S integration handoff.

## 7. Verification commands to keep using

Before committing functional changes or reinstalling the APK, keep running:

```bash
bash scripts/check_android_env.sh
bash scripts/test_unit.sh
bash scripts/build_debug.sh
bash scripts/lint_debug.sh
bash scripts/package_debug.sh
bash scripts/check_apk_permissions.sh
```

For documentation-only changes, at minimum run:

```bash
git diff --check
```

# Current handoff: local KWS fix, command coverage, and Redis interface waiting point

Date: 2026-06-08
Project: `VehicleOfflineVoice`
Path: `/home/weathour/document/programs/chongqingUNITY/android-apk`

## 1. Current repository state

The repository is at a real-device offline voice-loop baseline plus one local KWS robustness fix.

Latest important commits:

```text
51848ac fix(kws): tolerate nihao cheji wake variants
9822cfc docs: summarize real-device voice baseline
108f12d fix(device): use unrestricted vosk command asr
b589cf5 feat(device): add in-app asr tts debug panel
e5ad6d3 fix(device): use unrestricted vosk wake recognition
```

Current debug APK path:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 2. Real-device baseline before this handoff

Latest observed phone debugging confirmed that the core real offline loop works for the air-conditioner commands:

```text
小车小车 -> 打开 空调 -> air_conditioner_on -> 已为你打开空调
小车小车 -> 关闭 空调 -> air_conditioner_off -> 已为你关闭空调
```

Confirmed working together:

- real microphone capture;
- Vosk local wake recognition;
- VAD utterance endpointing;
- Vosk offline ASR;
- rule NLU;
- local/Mock vehicle state mutation;
- Chinese TTS reply;
- Unity action JSON logging;
- in-app debug panel for phone-side testing.

Known phone-side issue before this handoff:

- `小车小车` wakes normally;
- user reported `你好车机` did not wake reliably.

## 3. Local fix completed for `你好车机`

The phone was not used for this fix. Work was done only in the current computer development environment.

Implemented:

- added `WakePhraseMatcher`;
- updated `VoskKeywordSpotter` to use the matcher;
- added unit coverage in `WakePhraseMatcherTest`.

`你好车机` now tolerates variants and common small-model ASR confusions such as:

```text
你好车机
你好 车机
你 好 车 机
您好车机
您好 车 机
你好车技
您好车技
你好车击
您好车击
你好成绩
您好成绩
```

Safety boundary:

- `你好` alone does not wake;
- unrelated commands such as `打开空调` do not wake without a matched wake phrase.

This still needs a later phone reinstall and real microphone check to confirm actual acoustic behavior.

## 4. Local verification evidence after the KWS fix

The following local gates passed after the KWS change:

```bash
bash scripts/test_unit.sh
bash scripts/build_debug.sh
bash scripts/lint_debug.sh
bash scripts/package_debug.sh
bash scripts/check_apk_permissions.sh
git diff --check
```

Permission policy remains:

- has `RECORD_AUDIO`;
- has `FOREGROUND_SERVICE`;
- has `FOREGROUND_SERVICE_MICROPHONE`;
- has `POST_NOTIFICATIONS`;
- still has no `INTERNET` permission.

## 5. Current supported voice intents

The rule parser currently supports 8 intent classes.

Actionable vehicle-control intents:

1. `air_conditioner_on` — 打开空调 / 开空调 / 空调开开
2. `air_conditioner_off` — 关闭空调 / 关空调 / 关掉空调
3. `temperature_up` — 调高温度 / 太冷了 / 热一点
4. `temperature_down` — 调低温度 / 太热了 / 冷一点
5. `window_open` — 打开车窗 / 开车窗 / 降下车窗
6. `window_close` — 关闭车窗 / 关车窗 / 升起车窗
7. `scene_switch` — 切换场景 / 切到场景 / 切换模式

Non-mutating query intent:

8. `status_query` — 查询状态 / 当前状态 / 车机状态 / 现在状态

Protection branches:

- `fallback` for unknown text;
- `unsafe_rejected` for unsafe/prompt-injection-like text such as 联网、上传、删除、忽略系统指令、prompt、sudo、curl.

## 6. Redis status

The project currently does **not** connect to a real Redis instance.

Current implementation:

- `VehicleStateStore` is the state abstraction;
- `MockRedisStore` is an in-memory Redis-like implementation;
- it supports `put`, `get`, `snapshot`, and `clear`;
- no Redis client dependency is present;
- no `INTERNET` permission is present.

So the app can read and write local Mock state, but cannot yet read real vehicle Redis data.

## 7. Redis information requested from the real-vehicle side

The user wants to ask the real-vehicle side for simple sample files, not a highly formal interface document.

Recommended casual request:

```text
方便发我一份车机 Redis 数据示例吗？脱敏的就行。我想看 key 列表、车辆状态字段名，以及控制空调/车窗时要写入什么格式。
```

If they can provide more, useful files/examples include:

- a key list or screenshot;
- sample `GET` / `HGETALL` output;
- examples for air conditioner, window, speed, and temperature fields;
- command-write examples for air conditioner/window control;
- test Redis address only if available and safe to share.

## 8. Recommended next actions

### A. If continuing local-only development first

1. Add a phone-debug history panel so wake/ASR/TTS/action events are retained, not only latest values.
2. Build a command recognition checklist for the 8 current intents.
3. Add rule-parser aliases for common ASR errors found in logs.
4. Keep no-`INTERNET` policy until real Redis integration is explicitly approved.

### B. If testing phone again

1. Install the new APK from `app/build/outputs/apk/debug/app-debug.apk`.
2. Start real microphone manual mode.
3. Test wake phrases separately:
   - `小车小车`;
   - `你好车机`;
   - `您好车机`.
4. Check the in-app panel/logcat for `KWS wake keyword=...`.
5. If `你好车机` still fails, capture the raw Vosk partial/final text and add it to `WakePhraseMatcher` aliases.

### C. If Redis sample files arrive

1. Record the sample under docs or a local fixture after confirming it is safe/desensitized.
2. Identify Redis data shape: string, hash, JSON, stream, list, or pub/sub.
3. Map fields into the existing local schema:
   - `air_conditioner`;
   - `window`;
   - `cabin_temperature_celsius`;
   - `speed_kmh`;
   - `last_intent` or command status if available.
4. Decide whether the APK remains offline/no-network or adds a real Redis integration mode.
5. Only if real Redis is approved, add a separate adapter behind `VehicleStateStore` instead of replacing `MockRedisStore`.

## 9. Do not forget

- Do not remove mock/scripted paths; they are needed for local regression tests.
- Do not add `INTERNET` permission unless the project intentionally moves into real Redis/network integration.
- Real Unity bridge and RK3588S migration are still later stages.

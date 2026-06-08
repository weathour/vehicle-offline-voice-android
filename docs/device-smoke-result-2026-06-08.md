# Device smoke result: ordinary Android phone

Date: 2026-06-08
Build: debug APK from `app/build/outputs/apk/debug/app-debug.apk`

## Observed result

The user confirmed the APK behavior matched the expected pre-device/mock-chain behavior:

- APK installed successfully.
- App launched successfully.
- Runtime permission dialogs appeared normally.
- `启动语音服务` and `停止语音服务` UI actions produced logs.
- Updated service-to-UI logging shows the expected mock pipeline output in the app page.

## Interpretation

This confirms the current APK is suitable as the pre-device handoff baseline.

It does **not** prove real microphone wake-word recognition or real ASR yet. Those are next-stage goals documented in `docs/handoff-next-stage.md`.

## Later real-device voice-loop update

After the initial mock-chain smoke test, the same phone-debugging workflow was extended to the real offline loop.

Observed successful path:

```text
小车小车 -> 打开 空调 -> air_conditioner_on -> 已为你打开空调
小车小车 -> 关闭 空调 -> air_conditioner_off -> 已为你关闭空调
```

Current interpretation:

- real microphone capture is working;
- local wake detection is working for `小车小车`;
- offline ASR can recognize the A/C open/close commands;
- rule NLU, TTS reply, and Unity action JSON emission are working;
- the main remaining issue is robustness for the full command set and common ASR misrecognitions.

See `docs/current-real-device-voice-status-2026-06-08.md` for the current baseline and next-stage plan.

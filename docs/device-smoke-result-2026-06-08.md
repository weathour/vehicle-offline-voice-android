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

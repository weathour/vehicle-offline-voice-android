# Final phone/device test checklist

Do not run this checklist until local pre-device verification has passed.

## Ordinary Android phone

1. Run `bash scripts/package_debug.sh` locally.
2. Connect one phone with USB debugging enabled.
3. Run `bash scripts/final_install_phone.sh`.
4. Grant microphone and notification permissions.
5. Start `Vehicle Offline Voice` and tap `启动语音服务`.
6. Watch logs with `bash scripts/final_logcat.sh`.
7. Confirm foreground notification appears and no crash occurs.
8. Confirm preview mock pipeline logs KWS, VAD, ASR, NLU, TTS, and Unity JSON.

## RK3588S Android 13 target later

- Repeat the same install/logcat flow.
- Then replace scripted KWS/ASR/TTS with real offline engines and rerun command tests.
- Only after Unity IPC is ready, connect the Unity action sink.

## Explicitly deferred before this phase

- No earlier phone install.
- No parent Unity project usage.
- No real Redis.
- No Internet/cloud ASR.

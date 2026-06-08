# Voice pipeline behavior before device testing

## Normal fake-chain scenario

1. Fake PCM source emits silence.
2. Scripted KWS emits a wake event.
3. Energy VAD waits for sustained speech and ends after silence.
4. Scripted ASR returns Chinese text such as `打开空调`.
5. Rule NLU maps the text to `air_conditioner_on`.
6. `MockRedisStore` records `air_conditioner=on` and `last_intent=air_conditioner_on`.
7. Reply template returns `已为你打开空调` and mock TTS records it.
8. Unity action JSON is generated and logged.

## Fallback and safety

- No wake word: no ASR, no state mutation, no Unity JSON.
- Unknown command: fallback reply, no mutation, no Unity JSON.
- Unsafe text such as attempts to ignore instructions, delete state, or upload data: rejected reply, no mutation, no Unity JSON.

## Future replacements

- Replace `ScriptedKeywordSpotter` with a real offline KWS engine.
- Replace `ScriptedAsrEngine` with sherpa-onnx/Vosk/Whisper.cpp or another offline ASR.
- Replace `MockTtsEngine` with Android `TextToSpeech` or an offline TTS engine.
- Replace `RecordingUnityEventSink` with the final Unity IPC bridge after the APK pipeline is proven.

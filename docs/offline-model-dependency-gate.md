# Offline model/dependency gate

Date: 2026-06-08

## Selected first implementation path

Use Vosk Android as the first real offline ASR-backed phrase-spotting adapter path for this repository pass.

## Candidate matrix

| Candidate | Android support | KWS | ASR | Offline model strategy | Decision |
| --- | --- | --- | --- | --- | --- |
| sherpa-onnx | Official Android docs; supports local ASR/KWS/TTS/VAD | Yes | Yes | Prebuilt Android libs plus model assets | Keep as future production candidate; immediate packaging surface is larger |
| Vosk Android | Official Android demo and Maven AAR | Restricted-grammar phrase spotting | Yes | AAR + packaged/copied model directory | Chosen first |
| whisper.cpp | Android possible | No dedicated KWS | Yes | Packaged whisper model | Rejected for KWS-first command loop |

## Vosk dependency pins

- `net.java.dev.jna:jna:5.18.1@aar`
- `com.alphacephei:vosk-android:0.3.75@aar`

## Model asset policy

- Runtime downloads are not allowed.
- APK must not request `INTERNET`.
- First target model: `vosk-model-small-cn-0.22`, listed by Vosk as a lightweight Android/RPi Chinese model under Apache 2.0.
- If the model directory is absent, production Vosk adapters fail fast and the app should use only explicit mock/virtual modes.

## Verification

Run after dependency/model changes:

```bash
bash scripts/build_debug.sh
bash scripts/check_apk_permissions.sh
```


## Packaged asset evidence

The first offline model asset has been packaged under:

```text
app/src/main/assets/model-cn
```

This directory contains `vosk-model-small-cn-0.22` extracted as `model-cn`. The runtime installer copies it to app-private storage before constructing Vosk `Model` instances. The APK permission guard still confirms no `INTERNET` permission after adding the Vosk AAR and model assets.

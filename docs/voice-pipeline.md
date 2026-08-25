# 语音链路行为

## 正式麦克风模式

1. `AndroidAudioRecordSource` 采集 16 kHz 单声道 PCM。
2. 离线 KWS 检测“小车小车”，固定 WAV 立即回复“我在”。
3. VAD 截取指令音频，Vosk 在设备端完成中文 ASR。
4. 规则 NLU 解析只读车辆查询并读取 Redis/protobuf 快照。
5. 回复模板生成文字，默认由内置 24 kHz Kokoro 中英模型合成 PCM，经响度校正后由 `AudioTrack` 播放。
6. 用户也可在主界面选择 Android 系统 TTS 优先；首选不可用时尝试另一后端，两者均失败时播放固定 WAV 故障提示。
7. 每次播报期间暂停麦克风，结束后恢复采集并重置 KWS/VAD。

## 测试用 fake-chain

1. Fake PCM source emits silence.
2. Scripted KWS emits a wake event.
3. Energy VAD waits for sustained speech and ends after silence.
4. Scripted ASR returns Chinese text such as `打开空调`.
5. Rule NLU maps the text to `air_conditioner_on`.
6. `MockRedisStore` records `air_conditioner=on` and `last_intent=air_conditioner_on`.
7. Reply template returns `已为你打开空调` and mock TTS records it.
8. Unity action JSON is generated and logged.

## 回退与安全

- No wake word: no ASR, no state mutation, no Unity JSON.
- Unknown command: fallback reply, no mutation, no Unity JSON.
- Unsafe text such as attempts to ignore instructions, delete state, or upload data: rejected reply, no mutation, no Unity JSON.
- 实车模式拒绝车控写操作，只允许读取车辆状态。
- 语音选择保存在应用偏好中，并随启动 Intent 传给前台服务；重新启动服务即可切换顺序。
- 普通英文不再逐字母转成中文音名；仅保留 V2X、SOC、RTK 等明确领域缩写的定向读法。
- TTS 后端故障会在当前服务会话中停用，避免每次回复重复初始化和超时。
- TTS 失败不回滚已经完成的只读查询；日志会记录实际使用的后端和失败原因。

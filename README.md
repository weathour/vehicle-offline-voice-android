# 渝行智声车载语音交互软件

“渝行智声”v1.4 面向手机和车载 Android 终端。设备连接车辆只读 Redis 数据源后，可用离线语音识别查询车速、电量、告警和协作场景，并通过可选 TTS 播报回答。

著作权人：长安大学。主要开发人员：杨兴杰、陈婷、徐志刚、王嘉鑫、申丹丹。正式包名：`cn.edu.chd.yuxingvoice`。

## 当前能力

- 唤醒词、VAD、Vosk ASR 和规则 NLU 全部离线运行。
- 可选 Edge、百度、腾讯云三种在线 TTS，或设备自带的 Android 系统 TTS。
- Edge 默认免注册；百度和腾讯云通过用户提供的 JSON 配置启用，密钥导入后由 Android Keystore 加密保存，不进入 APK、日志或普通偏好设置。
- 在线 TTS 失败时自动尝试系统 TTS；仍不可用时播放固定 WAV 提示。
- 主界面始终显示“当前播报内容”。即使设备没有 TTS、没有网络或凭据失效，回答文字仍可读取。
- TTS 播放期间暂停麦克风采集，避免扬声器回声再次进入识别链路。
- 车辆数据访问只执行 Redis `GET`，当前版本不发真实车辆控制命令。

## 使用流程

1. 设备连接车辆网络和互联网；若选择系统 TTS，只需车辆网络。
2. 安装并打开 APK，填写 Redis IP、端口和 DB。
3. 选择 TTS：
   - **Edge 在线语音**：默认选项，无需注册。
   - **百度在线语音 / 腾讯云在线语音**：先导入自己的 `tts-config.json`。
   - **Android 系统语音**：设备已安装中文 TTS 时可用。
4. 点击 **试听当前语音**。
5. 点击 **检测车辆数据连接**，确认 `connected=true` 且解码状态正常。
6. 点击 **启动语音服务**，说“小车小车，当前车速多少”等查询。

## TTS 配置文件

复制根目录的 [`tts-config.example.json`](tts-config.example.json)，填入自己申请的凭据并删除不用的供应商段，然后在应用内点击 **导入 TTS 配置文件**。不要把真实配置提交到 Git；`tts-config.json` 已加入 `.gitignore`。

```json
{
  "version": 1,
  "defaultProvider": "baidu",
  "baidu": {
    "appId": "实际 App ID",
    "apiKey": "实际 API Key",
    "secretKey": "实际 Secret Key",
    "voice": 0,
    "speed": 5,
    "pitch": 5,
    "volume": 9
  }
}
```

`defaultProvider` 可取 `edge`、`baidu`、`tencent` 或 `system`。应用不需要自建服务器；设备会直接请求所选服务。Edge 使用浏览器朗读接口，不提供可承诺的 SLA，接口变化时会自动回退系统 TTS。百度、腾讯云是否免费及额度以各自账号当时的控制台为准。

## 本地模拟 Redis

```bash
SIM_REDIS_HOST=0.0.0.0 SIM_REDIS_PORT=6379 bash scripts/start_sim_redis.sh
python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 defaults
```

## 开发验证

```bash
./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest :app:lintRelease
RUN_ONLINE_TTS_TESTS=1 ./gradlew :app:testDebugUnitTest --tests 'com.company.vehiclevoice.tts.*'
./gradlew :app:assembleDebug :app:assembleRelease
scripts/check_apk_permissions.sh
scripts/smoke_sim_redis.sh
git diff --check
```

Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 重要边界

- `INTERNET` 权限用于只读 Redis 和所选在线 TTS；ASR 音频不会上传。
- 选择在线 TTS 时，本次要播报的回答文字会发送给对应供应商。
- Edge 无需密钥；百度、腾讯云凭据必须由使用者自行注册和管理。本仓库与 Release 均不包含真实 API 密钥。
- Vosk restricted grammar 暂不启用；中文整句 grammar 会导致 `[unk]`，当前采用开放 ASR 加 NLU 领域纠错。

## 文档入口

- 架构说明：[`docs/architecture.md`](docs/architecture.md)
- v1.4 发布说明：[`docs/releases/v1.4-configurable-online-tts.md`](docs/releases/v1.4-configurable-online-tts.md)
- 开发环境：[`docs/development-environment.md`](docs/development-environment.md)
- 语音链路：[`docs/voice-pipeline.md`](docs/voice-pipeline.md)
- 第三方软件：[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)

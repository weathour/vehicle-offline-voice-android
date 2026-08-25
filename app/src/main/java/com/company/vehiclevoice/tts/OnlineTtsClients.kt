package com.company.vehiclevoice.tts

import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class TtsAudio(
    val bytes: ByteArray,
    val fileSuffix: String
) {
    init {
        require(bytes.isNotEmpty()) { "在线 TTS 没有返回音频" }
        require(fileSuffix == ".mp3" || fileSuffix == ".wav") { "不支持的在线 TTS 音频格式" }
    }
}

interface OnlineTtsClient {
    fun synthesize(text: String): TtsAudio
}

internal fun createOnlineTtsClient(provider: TtsProvider, config: OnlineTtsConfig): OnlineTtsClient = when (provider) {
    TtsProvider.Edge -> EdgeTtsClient(config.edge)
    TtsProvider.Baidu -> BaiduTtsClient(checkNotNull(config.baidu))
    TtsProvider.Tencent -> TencentTtsClient(checkNotNull(config.tencent))
    TtsProvider.System -> error("系统 TTS 不是在线 TTS 客户端")
}

private object SharedHttpClient {
    val instance: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()
}

internal class EdgeTtsClient(
    private val config: EdgeTtsConfig,
    private val httpClient: OkHttpClient = SharedHttpClient.instance
) : OnlineTtsClient {
    override fun synthesize(text: String): TtsAudio {
        val speechText = sanitizeText(text)
        require(speechText.isNotBlank()) { "Edge TTS 文本为空" }
        require(speechText.toByteArray(StandardCharsets.UTF_8).size <= MAX_TEXT_BYTES) {
            "Edge TTS 文本过长"
        }
        return try {
            synthesizeOnce(speechText)
        } catch (failure: EdgeConnectionException) {
            if (failure.statusCode != 403 || failure.serverDate == null) throw failure
            val serverTime = ZonedDateTime.parse(
                failure.serverDate,
                DateTimeFormatter.RFC_1123_DATE_TIME
            ).toEpochSecond()
            clockSkewSeconds.set(serverTime - Instant.now().epochSecond)
            synthesizeOnce(speechText)
        }
    }

    private fun synthesizeOnce(text: String): TtsAudio {
        val audio = ByteArrayOutputStream()
        val done = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val sawTurnEnd = AtomicBoolean(false)
        val request = Request.Builder()
            .url(webSocketUrl())
            .header("User-Agent", USER_AGENT)
            .header("Origin", EDGE_EXTENSION_ORIGIN)
            .header("Pragma", "no-cache")
            .header("Cache-Control", "no-cache")
            .header("Cookie", "muid=${connectionId().uppercase(Locale.US)};")
            .build()

        var webSocket: WebSocket? = null
        fun fail(throwable: Throwable) {
            if (failure.compareAndSet(null, throwable)) {
                webSocket?.cancel()
                done.countDown()
            }
        }

        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(socket: WebSocket, response: Response) {
                val sentConfig = socket.send(speechConfigMessage())
                val sentSpeech = socket.send(ssmlMessage(text))
                if (!sentConfig || !sentSpeech) fail(IllegalStateException("Edge TTS 请求发送失败"))
            }

            override fun onMessage(socket: WebSocket, textMessage: String) {
                when (protocolPath(textMessage)) {
                    "turn.end" -> {
                        sawTurnEnd.set(true)
                        socket.close(1000, null)
                        done.countDown()
                    }
                    "response", "turn.start", "audio.metadata" -> Unit
                    null -> fail(IllegalStateException("Edge TTS 返回了未知文本消息"))
                }
            }

            override fun onMessage(socket: WebSocket, bytes: ByteString) {
                runCatching {
                    val packet = bytes.toByteArray()
                    require(packet.size >= 2) { "Edge TTS 音频消息损坏" }
                    val headerLength = ((packet[0].toInt() and 0xff) shl 8) or
                        (packet[1].toInt() and 0xff)
                    require(headerLength >= 0 && headerLength + 2 <= packet.size) { "Edge TTS 音频头长度无效" }
                    val header = String(packet, 2, headerLength, StandardCharsets.UTF_8)
                    require(protocolPath(header) == "audio") { "Edge TTS 返回了非音频二进制消息" }
                    val bodyOffset = headerLength + 2
                    if (bodyOffset < packet.size) {
                        require(audio.size() + packet.size - bodyOffset <= MAX_AUDIO_BYTES) { "Edge TTS 音频过大" }
                        audio.write(packet, bodyOffset, packet.size - bodyOffset)
                    }
                }.onFailure(::fail)
            }

            override fun onClosed(socket: WebSocket, code: Int, reason: String) {
                if (!sawTurnEnd.get() && failure.get() == null) {
                    fail(IllegalStateException("Edge TTS 连接提前关闭"))
                }
            }

            override fun onFailure(socket: WebSocket, throwable: Throwable, response: Response?) {
                val edgeFailure = EdgeConnectionException(
                    statusCode = response?.code,
                    serverDate = response?.header("Date"),
                    cause = throwable
                )
                response?.close()
                fail(edgeFailure)
            }
        })

        try {
            check(done.await(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "Edge TTS 请求超时" }
        } finally {
            if (!sawTurnEnd.get()) webSocket?.cancel()
        }
        failure.get()?.let { throw it }
        check(audio.size() > MIN_AUDIO_BYTES) { "Edge TTS 没有返回有效音频" }
        return TtsAudio(audio.toByteArray(), ".mp3")
    }

    private fun webSocketUrl(): String = "$WSS_URL?TrustedClientToken=$TRUSTED_CLIENT_TOKEN" +
        "&ConnectionId=${connectionId()}&Sec-MS-GEC=${secMsGec()}" +
        "&Sec-MS-GEC-Version=$SEC_MS_GEC_VERSION"

    private fun speechConfigMessage(): String =
        "X-Timestamp:${dateToString()}\r\n" +
            "Content-Type:application/json; charset=utf-8\r\n" +
            "Path:speech.config\r\n\r\n" +
            "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":" +
            "{\"sentenceBoundaryEnabled\":\"true\",\"wordBoundaryEnabled\":\"false\"}," +
            "\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}\r\n"

    private fun ssmlMessage(text: String): String {
        val requestId = connectionId()
        val voice = config.voice.split('-', limit = 3).let { parts ->
            "Microsoft Server Speech Text to Speech Voice (${parts[0]}-${parts[1]}, ${parts[2]})"
        }
        val ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='zh-CN'>" +
            "<voice name='${xmlEscape(voice)}'><prosody pitch='+0Hz' rate='${config.rate}' volume='${config.volume}'>" +
            xmlEscape(text) + "</prosody></voice></speak>"
        return "X-RequestId:$requestId\r\n" +
            "Content-Type:application/ssml+xml\r\n" +
            "X-Timestamp:${dateToString()}Z\r\n" +
            "Path:ssml\r\n\r\n$ssml"
    }

    private fun secMsGec(): String {
        var seconds = Instant.now().epochSecond + clockSkewSeconds.get() + WINDOWS_EPOCH_SECONDS
        seconds -= seconds % 300
        val input = "$seconds" + "0000000" + TRUSTED_CLIENT_TOKEN
        return sha256Hex(input.toByteArray(StandardCharsets.US_ASCII)).uppercase(Locale.US)
    }

    private fun dateToString(): String = EDGE_DATE_FORMAT.format(
        Instant.ofEpochSecond(Instant.now().epochSecond + clockSkewSeconds.get())
    )

    private class EdgeConnectionException(
        val statusCode: Int?,
        val serverDate: String?,
        cause: Throwable
    ) : IllegalStateException("Edge TTS 连接失败${statusCode?.let { " HTTP $it" }.orEmpty()}", cause)

    companion object {
        // Protocol constants track edge-tts 7.2.6 commit 4bdb8e4c6ea62f151a45a3fceb4cf6ff696bb89f.
        private const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
        private const val WSS_URL = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"
        private const val CHROMIUM_VERSION = "143.0.3650.75"
        private const val SEC_MS_GEC_VERSION = "1-$CHROMIUM_VERSION"
        private const val EDGE_EXTENSION_ORIGIN = "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0"
        private const val WINDOWS_EPOCH_SECONDS = 11_644_473_600L
        private const val MAX_TEXT_BYTES = 4_096
        private const val MAX_AUDIO_BYTES = 10 * 1024 * 1024
        private const val MIN_AUDIO_BYTES = 256
        private const val REQUEST_TIMEOUT_SECONDS = 15L
        private val clockSkewSeconds = AtomicLong(0)
        private val EDGE_DATE_FORMAT = DateTimeFormatter
            .ofPattern("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US)
            .withZone(ZoneOffset.UTC)
    }
}

internal class BaiduTtsClient(
    private val config: BaiduTtsConfig,
    private val httpClient: OkHttpClient = SharedHttpClient.instance
) : OnlineTtsClient {
    @Volatile private var accessToken: String? = null
    @Volatile private var tokenExpiresAtMs: Long = 0

    override fun synthesize(text: String): TtsAudio {
        require(text.toByteArray(Charsets.UTF_8).size <= 1_024) { "百度 TTS 文本超过 1024 字节" }
        val encodedOnce = URLEncoder.encode(text, StandardCharsets.UTF_8.name())
        val body = FormBody.Builder()
            .add("tex", encodedOnce)
            .add("per", config.voice.toString())
            .add("spd", config.speed.toString())
            .add("pit", config.pitch.toString())
            .add("vol", config.volume.toString())
            .add("cuid", config.appId)
            .add("tok", token())
            .add("aue", "3")
            .add("lan", "zh")
            .add("ctp", "1")
            .build()
        val request = Request.Builder().url(BAIDU_TTS_URL).post(body).build()
        httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "百度 TTS 请求失败 HTTP ${response.code}" }
            val contentType = response.header("Content-Type").orEmpty()
            val bytes = response.body.requireBody().readLimited(MAX_AUDIO_BYTES)
            check(contentType.startsWith("audio/")) {
                val message = runCatching { JSONObject(String(bytes, StandardCharsets.UTF_8)).optString("err_msg") }.getOrNull()
                "百度 TTS 合成失败${message?.let { "：$it" }.orEmpty()}"
            }
            return TtsAudio(bytes, ".mp3")
        }
    }

    @Synchronized
    private fun token(): String {
        accessToken?.takeIf { System.currentTimeMillis() + TOKEN_REFRESH_MARGIN_MS < tokenExpiresAtMs }?.let { return it }
        val url = BAIDU_TOKEN_URL.toHttpUrl().newBuilder()
            .addQueryParameter("grant_type", "client_credentials")
            .addQueryParameter("client_id", config.apiKey)
            .addQueryParameter("client_secret", config.secretKey)
            .build()
        httpClient.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            check(response.isSuccessful) { "百度 TTS 鉴权失败 HTTP ${response.code}" }
            val json = JSONObject(String(response.body.requireBody().readLimited(MAX_JSON_BYTES), StandardCharsets.UTF_8))
            val token = json.optString("access_token")
            check(token.isNotBlank()) { "百度 TTS 未返回 access_token" }
            val scope = json.optString("scope")
            check(scope.isBlank() || "audio_tts_post" in scope) { "百度应用未开通在线语音合成" }
            accessToken = token
            tokenExpiresAtMs = System.currentTimeMillis() + json.optLong("expires_in", 0) * 1_000L
            return token
        }
    }

    companion object {
        private const val BAIDU_TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token"
        private const val BAIDU_TTS_URL = "https://tsn.baidu.com/text2audio"
        private const val TOKEN_REFRESH_MARGIN_MS = 60_000L
    }
}

internal class TencentTtsClient(
    private val config: TencentTtsConfig,
    private val httpClient: OkHttpClient = SharedHttpClient.instance
) : OnlineTtsClient {
    override fun synthesize(text: String): TtsAudio {
        require(text.length <= 150) { "腾讯云 TTS 文本超过 150 字符" }
        val payload = JSONObject().apply {
            put("Text", text)
            put("SessionId", connectionId())
            put("Volume", config.volume)
            put("Speed", config.speed)
            put("ProjectId", 0)
            put("ModelType", 1)
            put("VoiceType", config.voiceType)
            put("PrimaryLanguage", 1)
            put("SampleRate", config.sampleRate)
            put("Codec", "mp3")
            put("EnableSubtitle", false)
        }.toString()
        val timestamp = Instant.now().epochSecond
        val authorization = TencentTc3Signer.authorization(
            secretId = config.secretId,
            secretKey = config.secretKey,
            payload = payload,
            timestamp = timestamp
        )
        val request = Request.Builder()
            .url(TENCENT_URL)
            .header("Content-Type", CONTENT_TYPE)
            .header("Host", TENCENT_HOST)
            .header("X-TC-Action", "TextToVoice")
            .header("X-TC-Version", "2019-08-23")
            .header("X-TC-Timestamp", timestamp.toString())
            .header("X-TC-Language", "zh-CN")
            .header("Authorization", authorization)
            .post(payload.toRequestBody(CONTENT_TYPE.toMediaType()))
            .build()
        httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "腾讯云 TTS 请求失败 HTTP ${response.code}" }
            val root = JSONObject(String(response.body.requireBody().readLimited(MAX_JSON_BYTES), StandardCharsets.UTF_8))
            val result = root.getJSONObject("Response")
            result.optJSONObject("Error")?.let { error ->
                throw IllegalStateException("腾讯云 TTS 合成失败：${error.optString("Message", error.optString("Code"))}")
            }
            val audio = result.optString("Audio")
            check(audio.isNotBlank()) { "腾讯云 TTS 未返回音频" }
            val bytes = Base64.getDecoder().decode(audio)
            check(bytes.size <= MAX_AUDIO_BYTES) { "腾讯云 TTS 音频过大" }
            return TtsAudio(bytes, ".mp3")
        }
    }

    companion object {
        private const val TENCENT_HOST = "tts.tencentcloudapi.com"
        private const val TENCENT_URL = "https://$TENCENT_HOST/"
        internal const val CONTENT_TYPE = "application/json; charset=utf-8"
    }
}

internal object TencentTc3Signer {
    fun authorization(secretId: String, secretKey: String, payload: String, timestamp: Long): String {
        val date = Instant.ofEpochSecond(timestamp).atZone(ZoneOffset.UTC).toLocalDate().toString()
        val canonicalHeaders = "content-type:${TencentTtsClient.CONTENT_TYPE}\nhost:$HOST\n"
        val signedHeaders = "content-type;host"
        val canonicalRequest = "POST\n/\n\n$canonicalHeaders\n$signedHeaders\n" +
            sha256Hex(payload.toByteArray(StandardCharsets.UTF_8))
        val scope = "$date/$SERVICE/tc3_request"
        val stringToSign = "TC3-HMAC-SHA256\n$timestamp\n$scope\n" +
            sha256Hex(canonicalRequest.toByteArray(StandardCharsets.UTF_8))
        val secretDate = hmac(("TC3$secretKey").toByteArray(StandardCharsets.UTF_8), date)
        val secretService = hmac(secretDate, SERVICE)
        val secretSigning = hmac(secretService, "tc3_request")
        val signature = hmac(secretSigning, stringToSign).toHex()
        return "TC3-HMAC-SHA256 Credential=$secretId/$scope, SignedHeaders=$signedHeaders, Signature=$signature"
    }

    private fun hmac(key: ByteArray, value: String): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal(value.toByteArray(StandardCharsets.UTF_8))
    }

    private const val HOST = "tts.tencentcloudapi.com"
    private const val SERVICE = "tts"
}

private fun ResponseBody?.requireBody(): ResponseBody = this ?: error("在线 TTS 返回空响应")

private fun ResponseBody.readLimited(limit: Int): ByteArray {
    contentLength().takeIf { it >= 0 }?.let { require(it <= limit) { "在线 TTS 响应过大" } }
    val output = ByteArrayOutputStream()
    byteStream().use { input ->
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= limit) { "在线 TTS 响应过大" }
            output.write(buffer, 0, count)
        }
    }
    return output.toByteArray()
}

private fun protocolPath(message: String): String? = message
    .substringBefore("\r\n\r\n")
    .lineSequence()
    .map { it.trim() }
    .firstOrNull { it.startsWith("Path:", ignoreCase = true) }
    ?.substringAfter(':')
    ?.trim()

private fun sanitizeText(text: String): String = buildString(text.length) {
    text.forEach { character ->
        append(if (character.code in 0..8 || character.code in 11..12 || character.code in 14..31) ' ' else character)
    }
}

private fun xmlEscape(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("'", "&apos;")
    .replace("\"", "&quot;")

private fun connectionId(): String = UUID.randomUUID().toString().replace("-", "")

private fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .toHex()

private fun ByteArray.toHex(): String = joinToString("") {
    "%02x".format(Locale.US, it.toInt() and 0xff)
}

private const val MAX_AUDIO_BYTES = 10 * 1024 * 1024
private const val MAX_JSON_BYTES = 512 * 1024

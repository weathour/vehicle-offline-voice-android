package com.company.vehiclevoice.tts

import android.annotation.SuppressLint
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class OnlineTtsConfig(
    val defaultProvider: TtsProvider = TtsProvider.Edge,
    val edge: EdgeTtsConfig = EdgeTtsConfig(),
    val baidu: BaiduTtsConfig? = null,
    val tencent: TencentTtsConfig? = null
) {
    fun isConfigured(provider: TtsProvider): Boolean = when (provider) {
        TtsProvider.Edge, TtsProvider.System -> true
        TtsProvider.Baidu -> baidu != null
        TtsProvider.Tencent -> tencent != null
    }

    fun toJsonString(): String = JSONObject().apply {
        put("version", VERSION)
        put("defaultProvider", defaultProvider.wireValue)
        put("edge", JSONObject().apply {
            put("voice", edge.voice)
            put("rate", edge.rate)
            put("volume", edge.volume)
        })
        baidu?.let { value ->
            put("baidu", JSONObject().apply {
                put("appId", value.appId)
                put("apiKey", value.apiKey)
                put("secretKey", value.secretKey)
                put("voice", value.voice)
                put("speed", value.speed)
                put("pitch", value.pitch)
                put("volume", value.volume)
            })
        }
        tencent?.let { value ->
            put("tencent", JSONObject().apply {
                put("secretId", value.secretId)
                put("secretKey", value.secretKey)
                put("voiceType", value.voiceType)
                put("speed", value.speed)
                put("volume", value.volume)
                put("sampleRate", value.sampleRate)
            })
        }
    }.toString()

    companion object {
        const val VERSION = 1
        const val MAX_CONFIG_BYTES = 32 * 1024

        fun parse(raw: String): OnlineTtsConfig {
            require(raw.toByteArray(StandardCharsets.UTF_8).size <= MAX_CONFIG_BYTES) {
                "TTS 配置文件不能超过 32 KB"
            }
            val root = JSONObject(raw)
            root.requireOnly("version", "defaultProvider", "edge", "baidu", "tencent")
            require(root.optInt("version", -1) == VERSION) { "TTS 配置 version 必须为 $VERSION" }

            val edge = root.optionalObject("edge")?.let(::parseEdge) ?: EdgeTtsConfig()
            val baidu = root.optionalObject("baidu")?.let(::parseBaidu)
            val tencent = root.optionalObject("tencent")?.let(::parseTencent)
            val providerValue = if (root.has("defaultProvider")) {
                root.getString("defaultProvider")
            } else {
                "edge"
            }
            val provider = TtsProvider.entries.firstOrNull {
                it.wireValue.equals(providerValue, ignoreCase = true)
            } ?: throw IllegalArgumentException("未知的默认 TTS 引擎：$providerValue")
            require(
                provider == TtsProvider.Edge || provider == TtsProvider.System ||
                    (provider == TtsProvider.Baidu && baidu != null) ||
                    (provider == TtsProvider.Tencent && tencent != null)
            ) { "默认 TTS 引擎缺少对应配置" }
            return OnlineTtsConfig(provider, edge, baidu, tencent)
        }

        private fun parseEdge(json: JSONObject): EdgeTtsConfig {
            json.requireOnly("voice", "rate", "volume")
            val value = EdgeTtsConfig(
                voice = json.optString("voice", EdgeTtsConfig.DEFAULT_VOICE),
                rate = json.optString("rate", "+0%"),
                volume = json.optString("volume", "+20%")
            )
            require(EDGE_VOICE.matches(value.voice)) { "Edge voice 格式不正确" }
            require(PERCENT.matches(value.rate)) { "Edge rate 格式应为 +0% 或 -10%" }
            require(PERCENT.matches(value.volume)) { "Edge volume 格式应为 +20% 或 -10%" }
            return value
        }

        private fun parseBaidu(json: JSONObject): BaiduTtsConfig {
            json.requireOnly("appId", "apiKey", "secretKey", "voice", "speed", "pitch", "volume")
            val value = BaiduTtsConfig(
                appId = json.requiredSecret("appId"),
                apiKey = json.requiredSecret("apiKey"),
                secretKey = json.requiredSecret("secretKey"),
                voice = json.optInt("voice", 0),
                speed = json.optInt("speed", 5),
                pitch = json.optInt("pitch", 5),
                volume = json.optInt("volume", 9)
            )
            require(value.voice in 0..9999) { "百度 voice 超出范围" }
            require(value.speed in 0..15) { "百度 speed 必须在 0 到 15 之间" }
            require(value.pitch in 0..15) { "百度 pitch 必须在 0 到 15 之间" }
            require(value.volume in 0..15) { "百度 volume 必须在 0 到 15 之间" }
            return value
        }

        private fun parseTencent(json: JSONObject): TencentTtsConfig {
            json.requireOnly("secretId", "secretKey", "voiceType", "speed", "volume", "sampleRate")
            val value = TencentTtsConfig(
                secretId = json.requiredSecret("secretId"),
                secretKey = json.requiredSecret("secretKey"),
                voiceType = json.optLong("voiceType", 0),
                speed = json.optDouble("speed", 0.0),
                volume = json.optDouble("volume", 5.0),
                sampleRate = json.optInt("sampleRate", 16_000)
            )
            require(value.voiceType >= 0) { "腾讯 voiceType 不能为负数" }
            require(value.speed in -2.0..6.0) { "腾讯 speed 必须在 -2 到 6 之间" }
            require(value.volume in -10.0..10.0) { "腾讯 volume 必须在 -10 到 10 之间" }
            require(value.sampleRate in setOf(8_000, 16_000, 24_000)) { "腾讯 sampleRate 仅支持 8000、16000 或 24000" }
            return value
        }

        private val EDGE_VOICE = Regex("^[a-z]{2,3}-[A-Z]{2}-[A-Za-z0-9-]+Neural$")
        private val PERCENT = Regex("^[+-]\\d{1,3}%$")
    }
}

data class EdgeTtsConfig(
    val voice: String = DEFAULT_VOICE,
    val rate: String = "+0%",
    val volume: String = "+20%"
) {
    companion object {
        const val DEFAULT_VOICE = "zh-CN-XiaoxiaoNeural"
    }
}

data class BaiduTtsConfig(
    val appId: String,
    val apiKey: String,
    val secretKey: String,
    val voice: Int = 0,
    val speed: Int = 5,
    val pitch: Int = 5,
    val volume: Int = 9
)

data class TencentTtsConfig(
    val secretId: String,
    val secretKey: String,
    val voiceType: Long = 0,
    val speed: Double = 0.0,
    val volume: Double = 5.0,
    val sampleRate: Int = 16_000
)

class TtsConfigStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): OnlineTtsConfig {
        val iv = preferences.getString(KEY_IV, null) ?: return OnlineTtsConfig()
        val ciphertext = preferences.getString(KEY_CIPHERTEXT, null) ?: return OnlineTtsConfig()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, Base64.getDecoder().decode(iv))
        )
        val plaintext = cipher.doFinal(Base64.getDecoder().decode(ciphertext))
        return OnlineTtsConfig.parse(String(plaintext, StandardCharsets.UTF_8))
    }

    fun save(config: OnlineTtsConfig) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(config.toJsonString().toByteArray(StandardCharsets.UTF_8))
        preferences.edit()
            .putString(KEY_IV, Base64.getEncoder().encodeToString(cipher.iv))
            .putString(KEY_CIPHERTEXT, Base64.getEncoder().encodeToString(ciphertext))
            .apply()
    }

    @SuppressLint("ApplySharedPref")
    fun clear() {
        preferences.edit().clear().commit()
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }

    companion object {
        private const val PREFERENCES = "tts_secure_config"
        private const val KEY_IV = "iv"
        private const val KEY_CIPHERTEXT = "ciphertext"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "vehicle_voice_tts_config_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

private fun JSONObject.requireOnly(vararg allowed: String) {
    val allowedSet = allowed.toSet()
    val unknown = keys().asSequence().filterNot(allowedSet::contains).toList()
    require(unknown.isEmpty()) { "TTS 配置包含未知字段：${unknown.joinToString()}" }
}

private fun JSONObject.requiredSecret(name: String): String {
    val value = getString(name).trim()
    require(value.isNotEmpty()) { "TTS 配置缺少 $name" }
    require(value.length <= 512) { "TTS 配置字段 $name 过长" }
    require(!value.startsWith("YOUR_")) { "请把 $name 的示例占位符替换为真实配置" }
    return value
}

private fun JSONObject.optionalObject(name: String): JSONObject? {
    if (!has(name) || isNull(name)) return null
    return getJSONObject(name)
}

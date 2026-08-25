package com.company.vehiclevoice.tts

enum class TtsProvider(
    val wireValue: String,
    val displayName: String
) {
    Edge("edge", "Edge 在线语音"),
    Baidu("baidu", "百度在线语音"),
    Tencent("tencent", "腾讯云在线语音"),
    System("system", "Android 系统语音");

    val requiresCredentials: Boolean
        get() = this == Baidu || this == Tencent

    companion object {
        fun fromWireValue(value: String?): TtsProvider = entries.firstOrNull {
            it.wireValue.equals(value, ignoreCase = true)
        } ?: Edge
    }
}

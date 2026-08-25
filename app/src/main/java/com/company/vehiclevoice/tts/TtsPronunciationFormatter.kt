package com.company.vehiclevoice.tts

object TtsPronunciationFormatter {
    fun forSpeech(text: String): String {
        var speech = text
        REPLACEMENTS.forEach { (pattern, replacement) ->
            speech = speech.replace(pattern, replacement)
        }
        speech = speech.replace(PERCENT_REGEX) { "百分之${it.groupValues[1]}" }
        return speech.replace('_', ' ')
    }

    private val REPLACEMENTS = listOf(
        Regex("(?i)\\bV2V\\b") to "维突维",
        Regex("(?i)\\bV2I\\b") to "维突爱",
        Regex("(?i)\\bV2X\\b") to "维突艾克斯",
        Regex("(?i)(?<![A-Z])SOC(?![A-Z])") to "电池荷电状态",
        Regex("(?i)\\bRTK\\b") to "阿尔提开",
        Regex("(?i)\\bTPMS\\b") to "胎压监测",
        Regex("(?i)\\bACC\\b") to "自适应巡航",
        Regex("(?i)\\bLKA\\b") to "车道保持辅助",
        Regex("(?i)\\bSAM\\b") to "萨姆",
        Regex("(?i)\\bL2\\b") to "二级辅助驾驶"
    )
    private val PERCENT_REGEX = Regex("(-?\\d+(?:\\.\\d+)?)%")
}

package com.company.vehiclevoice.tts

object TtsPronunciationFormatter {
    fun forSpeech(text: String): String {
        var speech = text
        REPLACEMENTS.forEach { (pattern, replacement) ->
            speech = speech.replace(pattern, replacement)
        }
        speech = speech.replace(PERCENT_REGEX) { "百分之${it.groupValues[1]}" }
        speech = speech.replace(ASCII_WORD_REGEX) { match ->
            match.value.uppercase().map { LETTER_NAMES[it] ?: it.toString() }.joinToString("")
        }
        return speech.replace('_', ' ')
    }

    private val REPLACEMENTS = listOf(
        Regex("(?i)V2V") to "维突维",
        Regex("(?i)V2I") to "维突爱",
        Regex("(?i)V2X") to "维突艾克斯",
        Regex("(?i)Redis") to "瑞迪丝",
        Regex("(?i)schema") to "数据结构",
        Regex("(?i)SOC") to "电池荷电状态",
        Regex("(?i)RTK") to "阿尔提开",
        Regex("(?i)TPMS") to "胎压监测",
        Regex("(?i)ACC") to "自适应巡航",
        Regex("(?i)LKA") to "车道保持辅助",
        Regex("(?i)SAM") to "萨姆",
        Regex("(?i)L2") to "二级辅助驾驶"
    )
    private val PERCENT_REGEX = Regex("(-?\\d+(?:\\.\\d+)?)%")
    private val ASCII_WORD_REGEX = Regex("[A-Za-z]+")
    private val LETTER_NAMES = mapOf(
        'A' to "诶", 'B' to "比", 'C' to "西", 'D' to "迪", 'E' to "伊", 'F' to "艾弗",
        'G' to "吉", 'H' to "艾尺", 'I' to "爱", 'J' to "杰", 'K' to "开", 'L' to "艾勒",
        'M' to "艾姆", 'N' to "恩", 'O' to "欧", 'P' to "披", 'Q' to "丘", 'R' to "阿尔",
        'S' to "艾丝", 'T' to "提", 'U' to "优", 'V' to "维", 'W' to "达不溜",
        'X' to "艾克斯", 'Y' to "歪", 'Z' to "贼德"
    )
}

package com.company.vehiclevoice.kws

import java.util.Locale

internal class WakePhraseMatcher private constructor(
    private val profiles: List<WakePhraseProfile>
) {
    fun match(rawText: String): String? {
        val text = rawText.normalizedWakeText()
        if (text.isEmpty()) return null
        return profiles.firstNotNullOfOrNull { profile ->
            if (profile.matches(text)) profile.canonical else null
        }
    }

    private data class WakePhraseProfile(
        val canonical: String,
        val aliases: Set<String>,
        val fuzzyMatcher: ((String) -> Boolean)? = null
    ) {
        private val normalizedAliases = aliases.map { it.normalizedWakeText() }.filter { it.isNotEmpty() }.toSet()

        fun matches(text: String): Boolean =
            normalizedAliases.any { alias -> text.contains(alias) } || fuzzyMatcher?.invoke(text) == true
    }

    companion object {
        fun fromWakePhrases(wakePhrases: List<String>): WakePhraseMatcher {
            val aliasesByCanonical = linkedMapOf<String, MutableSet<String>>()
            wakePhrases.forEach { phrase ->
                val canonical = canonicalPhraseFor(phrase)
                val aliases = aliasesByCanonical.getOrPut(canonical) { linkedSetOf() }
                aliases += phrase
                aliases += generatedAliasesFor(canonical)
            }
            return WakePhraseMatcher(
                aliasesByCanonical.map { (canonical, aliases) ->
                    WakePhraseProfile(
                        canonical = canonical,
                        aliases = aliases,
                        fuzzyMatcher = fuzzyMatcherFor(canonical)
                    )
                }
            )
        }

        private fun canonicalPhraseFor(phrase: String): String = when (phrase.normalizedWakeText()) {
            "你好车机" -> "你好车机"
            "小车小车" -> "小车小车"
            else -> phrase
        }

        private fun generatedAliasesFor(canonical: String): Set<String> = when (canonical.normalizedWakeText()) {
            "你好车机" -> setOf(
                "你好车机",
                "你好 车机",
                "你好 车 机",
                "你 好 车 机",
                "您好车机",
                "您好 车机",
                "您好 车 机",
                // Common Chinese ASR confusions for che-ji on the small offline model.
                "你好车技",
                "您好车技",
                "你好车击",
                "您好车击",
                "你好成绩",
                "您好成绩"
            )
            "小车小车" -> setOf("小车小车", "小车 小车", "小 车 小 车")
            else -> setOf(canonical)
        }

        private fun fuzzyMatcherFor(canonical: String): ((String) -> Boolean)? = when (canonical.normalizedWakeText()) {
            "你好车机" -> ::looksLikeNiHaoCheJi
            else -> null
        }

        private fun looksLikeNiHaoCheJi(text: String): Boolean {
            val hasGreeting = listOf("你好", "您好").any { text.contains(it) }
            val hasCheJiLikeTail = listOf("车机", "车技", "车击", "成绩", "成机").any { text.contains(it) }
            return hasGreeting && hasCheJiLikeTail
        }
    }
}

internal fun String.normalizedWakeText(): String =
    lowercase(Locale.ROOT).replace(Regex("[\\s，。,.！？!?:：;；\\-]+"), "")

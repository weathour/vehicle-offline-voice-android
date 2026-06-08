package com.company.vehiclevoice.kws

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WakePhraseMatcherTest {
    private val matcher = WakePhraseMatcher.fromWakePhrases(
        listOf("你好车机", "你好 车机", "小车小车", "小车 小车")
    )

    @Test
    fun matchesNiHaoCheJiWithSpacesAndCharacterSplits() {
        assertEquals("你好车机", matcher.match("你好 车机"))
        assertEquals("你好车机", matcher.match("你 好 车 机"))
        assertEquals("你好车机", matcher.match("您好 车 机"))
    }

    @Test
    fun matchesCommonNiHaoCheJiAsrConfusions() {
        assertEquals("你好车机", matcher.match("你好 车技"))
        assertEquals("你好车机", matcher.match("您好 车击"))
        assertEquals("你好车机", matcher.match("你好 成绩"))
    }

    @Test
    fun stillMatchesXiaoCheXiaoChe() {
        assertEquals("小车小车", matcher.match("小车小车"))
        assertEquals("小车小车", matcher.match("小 车 小 车"))
    }

    @Test
    fun doesNotWakeOnGreetingAloneOrUnknownText() {
        assertNull(matcher.match("你好"))
        assertNull(matcher.match("打开空调"))
        assertNull(matcher.match("今天天气不错"))
    }
}

package com.company.vehiclevoice.nlu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AskableVoiceContentTest {
    private val parser = RuleIntentParser()

    @Test
    fun visibleSupportedQuestionsMatchParserIntents() {
        AskableVoiceContent.supportedQuestions.forEach { question ->
            val result = parser.parse(question.phrase)

            assertEquals(question.phrase, question.intent, result.intent.name)
            assertFalse(question.phrase, result.isActionable)
        }
    }

    @Test
    fun visibleContentIncludesCaveatedAndDeferredRealVehicleSections() {
        val titles = AskableVoiceContent.categories.map { it.title }

        assertTrue(titles.contains("定位、感知与轨迹"))
        assertTrue(titles.contains("Redis 数据诊断"))
        assertEquals("本轮不启用", AskableVoiceContent.categories.last().title)
        assertTrue(AskableVoiceContent.visiblePhraseList.contains("打开空调等控制写入"))
    }
}

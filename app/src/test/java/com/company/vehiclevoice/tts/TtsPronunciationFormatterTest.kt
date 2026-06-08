package com.company.vehiclevoice.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class TtsPronunciationFormatterTest {
    @Test
    fun formatsV2xFamilyForChineseSpeech() {
        assertEquals(
            "当前协作场景：V突I动态车速限制，类型V突I",
            TtsPronunciationFormatter.forSpeech("当前协作场景：V2I动态车速限制，类型V2I")
        )
        assertEquals(
            "协作类型包括 V突V、V突I、V突X",
            TtsPronunciationFormatter.forSpeech("协作类型包括 V2V、V2I、V2X")
        )
    }
}

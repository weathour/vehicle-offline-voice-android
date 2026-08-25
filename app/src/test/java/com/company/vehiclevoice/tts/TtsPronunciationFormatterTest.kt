package com.company.vehiclevoice.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsPronunciationFormatterTest {
    @Test
    fun formatsV2xFamilyForChineseSpeech() {
        assertEquals(
            "当前协作场景：维突爱动态车速限制，类型维突爱",
            TtsPronunciationFormatter.forSpeech("当前协作场景：V2I动态车速限制，类型V2I")
        )
        assertEquals(
            "协作类型包括 维突维、维突爱、维突艾克斯",
            TtsPronunciationFormatter.forSpeech("协作类型包括 V2V、V2I、V2X")
        )
    }

    @Test
    fun formatsEngineeringTermsForChineseOnlyModel() {
        val speech = TtsPronunciationFormatter.forSpeech(
            "Redis schema，SOC85%，RTK、ACC、LKA、TPMS、SAM，来源BC_Veh_Spd"
        )

        assertTrue(speech.contains("瑞迪丝 数据结构"))
        assertTrue(speech.contains("电池荷电状态百分之85"))
        assertTrue(speech.contains("阿尔提开、自适应巡航、车道保持辅助、胎压监测、萨姆"))
        assertFalse(speech.any { it in 'A'..'Z' || it in 'a'..'z' })
        assertFalse(speech.contains('_'))
    }
}

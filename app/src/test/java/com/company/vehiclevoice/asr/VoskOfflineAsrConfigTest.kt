package com.company.vehiclevoice.asr

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VoskOfflineAsrConfigTest {
    @Test
    fun defaultAsrDoesNotUseRestrictedGrammarForChineseCommands() {
        val source = File("src/main/java/com/company/vehiclevoice/asr/VoskOfflineAsrEngine.kt").readText()
        assertTrue(source.contains("private val grammar: List<String>? = null"))
    }
}

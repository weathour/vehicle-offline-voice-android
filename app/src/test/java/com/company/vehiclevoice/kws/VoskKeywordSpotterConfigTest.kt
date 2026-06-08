package com.company.vehiclevoice.kws

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VoskKeywordSpotterConfigTest {
    @Test
    fun defaultKwsDoesNotUseRestrictedGrammarForChineseWakePhrase() {
        val source = File("src/main/java/com/company/vehiclevoice/kws/VoskKeywordSpotter.kt").readText()
        assertTrue(source.contains("useRestrictedGrammar: Boolean = false"))
    }
}

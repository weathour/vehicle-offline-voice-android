package com.company.vehiclevoice.core

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceRuntimeModeTest {
    @Test
    fun fromWireValue_defaultsToPreviewMockForUnknownInput() {
        assertEquals(VoiceRuntimeMode.PreviewMock, VoiceRuntimeMode.fromWireValue(null))
        assertEquals(VoiceRuntimeMode.PreviewMock, VoiceRuntimeMode.fromWireValue("unknown"))
    }

    @Test
    fun fromWireValue_parsesAllModes() {
        VoiceRuntimeMode.entries.forEach { mode ->
            assertEquals(mode, VoiceRuntimeMode.fromWireValue(mode.wireValue))
        }
    }
}

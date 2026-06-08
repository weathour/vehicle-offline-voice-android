package com.company.vehiclevoice.core

enum class VoiceRuntimeMode(val wireValue: String, val displayName: String) {
    PreviewMock("preview_mock", "Preview Mock"),
    VirtualMicSmoke("virtual_mic_smoke", "Virtual Mic Smoke"),
    RealMicManual("real_mic_manual", "Real Mic Manual");

    companion object {
        const val EXTRA_NAME = "com.company.vehiclevoice.EXTRA_RUNTIME_MODE"

        fun fromWireValue(value: String?): VoiceRuntimeMode = entries.firstOrNull { it.wireValue == value } ?: PreviewMock
    }
}

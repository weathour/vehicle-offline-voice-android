package com.company.vehiclevoice.nlu

data class Slot(val name: String, val value: String)

data class VoiceIntent(
    val name: String,
    val slots: List<Slot> = emptyList(),
    val mutatesVehicleState: Boolean = true
) {
    fun slotMap(): Map<String, String> = slots.associate { it.name to it.value }

    companion object {
        val Fallback = VoiceIntent("fallback", mutatesVehicleState = false)
        val Unsafe = VoiceIntent("unsafe_rejected", mutatesVehicleState = false)
    }
}

data class ParseResult(
    val intent: VoiceIntent,
    val replyKey: String = intent.name,
    val confidence: Double = 1.0,
    val reason: String = "matched"
) {
    val isActionable: Boolean get() = intent.mutatesVehicleState && intent.name != VoiceIntent.Fallback.name
}

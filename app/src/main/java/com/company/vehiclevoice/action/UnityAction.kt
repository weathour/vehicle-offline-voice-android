package com.company.vehiclevoice.action

data class UnityAction(
    val type: String = "vehicle_action",
    val action: String,
    val slots: Map<String, String> = emptyMap(),
    val source: String = "voice",
    val timestampMs: Long
)

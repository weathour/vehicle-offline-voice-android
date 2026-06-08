package com.company.vehiclevoice.data

import com.company.vehiclevoice.nlu.ParseResult

class VehicleStateProjector(private val defaultTemperatureCelsius: Int = 24) {
    fun apply(parseResult: ParseResult, store: VehicleStateStore): Boolean {
        if (!parseResult.isActionable) return false
        val slots = parseResult.intent.slotMap()
        when (parseResult.intent.name) {
            "air_conditioner_on" -> store.put("air_conditioner", "on")
            "air_conditioner_off" -> store.put("air_conditioner", "off")
            "temperature_up" -> store.put("cabin_temperature_celsius", (currentTemperature(store) + 1).toString())
            "temperature_down" -> store.put("cabin_temperature_celsius", (currentTemperature(store) - 1).toString())
            "window_open" -> store.put("window", "open")
            "window_close" -> store.put("window", "closed")
            "scene_switch" -> store.put("scene", slots["scene"] ?: "next")
            else -> return false
        }
        store.put("last_intent", parseResult.intent.name)
        return true
    }

    private fun currentTemperature(store: VehicleStateStore): Int =
        store.get("cabin_temperature_celsius")?.toIntOrNull() ?: defaultTemperatureCelsius
}


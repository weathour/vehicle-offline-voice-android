package com.company.vehiclevoice.action

import com.company.vehiclevoice.nlu.ParseResult

class UnityActionMapper(private val clockMs: () -> Long = { System.currentTimeMillis() }) {
    fun map(parseResult: ParseResult): UnityAction? {
        if (!parseResult.isActionable) return null
        return UnityAction(
            action = parseResult.intent.name,
            slots = parseResult.intent.slotMap(),
            timestampMs = clockMs()
        )
    }
}

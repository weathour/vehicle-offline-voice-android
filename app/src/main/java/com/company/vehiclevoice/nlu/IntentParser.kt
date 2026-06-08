package com.company.vehiclevoice.nlu

interface IntentParser {
    fun parse(text: String): ParseResult
}

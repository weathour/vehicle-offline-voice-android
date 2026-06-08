package com.company.vehiclevoice.action

class UnityActionJsonEncoder {
    fun encode(action: UnityAction): String = buildString {
        append('{')
        appendField("type", action.type)
        append(',')
        appendField("action", action.action)
        append(',')
        append("\"slots\":")
        appendMap(action.slots)
        append(',')
        appendField("source", action.source)
        append(',')
        append("\"timestamp\":")
        append(action.timestampMs)
        append('}')
    }

    private fun StringBuilder.appendField(key: String, value: String) {
        append('"').append(escape(key)).append("\":\"").append(escape(value)).append('"')
    }

    private fun StringBuilder.appendMap(map: Map<String, String>) {
        append('{')
        map.entries.forEachIndexed { index, entry ->
            if (index > 0) append(',')
            append('"').append(escape(entry.key)).append("\":\"").append(escape(entry.value)).append('"')
        }
        append('}')
    }

    private fun escape(value: String): String = buildString {
        for (char in value) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char)
            }
        }
    }
}

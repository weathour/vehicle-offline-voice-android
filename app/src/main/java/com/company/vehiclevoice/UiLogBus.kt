package com.company.vehiclevoice

import java.util.concurrent.CopyOnWriteArraySet

object UiLogBus {
    private val listeners = CopyOnWriteArraySet<(String) -> Unit>()
    private val recentLines = ArrayDeque<String>()
    private const val MAX_RECENT_LINES = 200

    @Synchronized
    fun publish(message: String) {
        val line = "${System.currentTimeMillis()}  $message"
        recentLines += line
        while (recentLines.size > MAX_RECENT_LINES) recentLines.removeFirst()
        listeners.forEach { listener -> listener(line) }
    }

    @Synchronized
    fun addListener(listener: (String) -> Unit) {
        listeners += listener
        recentLines.forEach { listener(it) }
    }

    fun removeListener(listener: (String) -> Unit) {
        listeners -= listener
    }

    @Synchronized
    fun clear() {
        recentLines.clear()
    }
}

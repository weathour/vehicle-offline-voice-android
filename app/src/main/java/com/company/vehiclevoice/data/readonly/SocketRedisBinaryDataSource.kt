package com.company.vehiclevoice.data.readonly

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

/**
 * Minimal Redis RESP client for Phase 1 GET-only read access.
 *
 * This intentionally avoids adding a Redis SDK dependency. Each read opens a short-lived TCP
 * connection, optionally AUTH/SELECTs, sends GET, and closes. It is simple but sufficient for
 * phone-to-computer simulated Redis and later read-only vehicle cache tests.
 */
class SocketRedisBinaryDataSource(
    private val config: SocketRedisConfig,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) : BinaryVehicleDataSource {
    override val sourceName: String = "remote-redis://${config.host}:${config.port}/db${config.database}"

    @Volatile private var lastConnected: Boolean = false
    @Volatile private var lastDetail: String = "not connected yet"
    @Volatile private var successfulReads: Int = 0

    override fun read(key: String): VehicleBinaryValue? = read(key, timeoutMsOverride = null)

    fun read(key: String, timeoutMsOverride: Int? = null): VehicleBinaryValue? {
        require(key.isNotBlank()) { "Redis key must not be blank" }
        return try {
            val effectiveConfig = timeoutMsOverride?.let { config.copy(timeoutMs = it.coerceIn(50, 10_000)) } ?: config
            RedisRespSession(effectiveConfig).use { session ->
                val payload = session.get(key)
                lastConnected = true
                lastDetail = "endpoint=${effectiveConfig.host}:${effectiveConfig.port} db=${effectiveConfig.database} timeoutMs=${effectiveConfig.timeoutMs}"
                if (payload != null) successfulReads += 1
                payload?.let { VehicleBinaryValue(key = key, payload = it, updatedAtMs = clockMs()) }
            }
        } catch (throwable: Throwable) {
            lastConnected = false
            lastDetail = "endpoint=${config.host}:${config.port} error=${throwable.message ?: throwable::class.java.simpleName}"
            throw throwable
        }
    }

    override fun diagnostics(): VehicleDataSourceDiagnostics = VehicleDataSourceDiagnostics(
        sourceName = sourceName,
        connected = lastConnected,
        keyCount = successfulReads,
        detail = lastDetail
    )
}

private class RedisRespSession(private val config: SocketRedisConfig) : AutoCloseable {
    private val socket = Socket()
    private val input: BufferedInputStream
    private val output: BufferedOutputStream

    init {
        socket.connect(InetSocketAddress(config.host, config.port), config.timeoutMs)
        socket.soTimeout = config.timeoutMs
        input = BufferedInputStream(socket.getInputStream())
        output = BufferedOutputStream(socket.getOutputStream())
        config.password?.takeIf { it.isNotBlank() }?.let { password -> commandExpectOk("AUTH", password) }
        if (config.database != 0) commandExpectOk("SELECT", config.database.toString())
    }

    fun get(key: String): ByteArray? {
        writeCommand("GET", key)
        return when (val reply = readReply()) {
            RespNil -> null
            is RespBulk -> reply.bytes
            is RespSimple -> reply.text.toByteArray(StandardCharsets.UTF_8)
            is RespError -> throw IOException(reply.message)
            else -> throw IOException("unexpected Redis GET reply: ${reply::class.java.simpleName}")
        }
    }

    private fun commandExpectOk(vararg parts: String) {
        writeCommand(*parts)
        when (val reply = readReply()) {
            is RespSimple -> if (reply.text.uppercase() != "OK") throw IOException("unexpected Redis reply: ${reply.text}")
            is RespError -> throw IOException(reply.message)
            else -> throw IOException("unexpected Redis command reply: ${reply::class.java.simpleName}")
        }
    }

    private fun writeCommand(vararg parts: String) {
        output.write("*${parts.size}\r\n".toByteArray(StandardCharsets.UTF_8))
        parts.forEach { part ->
            val bytes = part.toByteArray(StandardCharsets.UTF_8)
            output.write("$${bytes.size}\r\n".toByteArray(StandardCharsets.UTF_8))
            output.write(bytes)
            output.write("\r\n".toByteArray(StandardCharsets.UTF_8))
        }
        output.flush()
    }

    private fun readReply(): RespReply {
        return when (val type = input.read()) {
            -1 -> throw IOException("Redis connection closed")
            '+'.code -> RespSimple(readLine())
            '-'.code -> RespError(readLine())
            ':'.code -> RespInteger(readLine().toLong())
            '$'.code -> readBulk()
            '*'.code -> readArray()
            else -> throw IOException("unsupported Redis RESP type byte: $type")
        }
    }

    private fun readBulk(): RespReply {
        val length = readLine().toInt()
        if (length < 0) return RespNil
        val bytes = input.readExactly(length)
        expectCrLf()
        return RespBulk(bytes)
    }

    private fun readArray(): RespArray {
        val count = readLine().toInt()
        if (count < 0) return RespArray(emptyList())
        return RespArray(List(count) { readReply() })
    }

    private fun readLine(): String {
        val out = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) throw IOException("Redis connection closed while reading line")
            if (b == '\r'.code) {
                val next = input.read()
                if (next != '\n'.code) throw IOException("malformed Redis line ending")
                return out.toString(StandardCharsets.UTF_8.name())
            }
            out.write(b)
        }
    }

    private fun expectCrLf() {
        val cr = input.read()
        val lf = input.read()
        if (cr != '\r'.code || lf != '\n'.code) throw IOException("malformed Redis bulk ending")
    }

    override fun close() {
        runCatching { socket.close() }
    }
}

private fun BufferedInputStream.readExactly(length: Int): ByteArray {
    val out = ByteArray(length)
    var offset = 0
    while (offset < length) {
        val read = read(out, offset, length - offset)
        if (read < 0) throw IOException("Redis bulk payload truncated")
        offset += read
    }
    return out
}

private sealed interface RespReply
private data class RespSimple(val text: String) : RespReply
private data class RespError(val message: String) : RespReply
private data class RespInteger(val value: Long) : RespReply
private data class RespBulk(val bytes: ByteArray) : RespReply
private data class RespArray(val values: List<RespReply>) : RespReply
private data object RespNil : RespReply

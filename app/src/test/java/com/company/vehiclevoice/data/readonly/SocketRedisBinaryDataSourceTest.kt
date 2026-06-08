package com.company.vehiclevoice.data.readonly

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class SocketRedisBinaryDataSourceTest {
    @Test
    fun readsBinaryBulkStringsFromRedisRespServer() {
        TinyRespRedis(mapOf(VehicleRedisKeys.SPEED to SimulatedVehicleRedisFixtures.speed(36.8f))).use { redis ->
            val source = SocketRedisBinaryDataSource(SocketRedisConfig(host = "127.0.0.1", port = redis.port, database = 1))

            val value = source.read(VehicleRedisKeys.SPEED)

            assertArrayEquals(SimulatedVehicleRedisFixtures.speed(36.8f), value!!.payload)
            assertTrue(source.diagnostics().connected)
            assertTrue(source.diagnostics().detail.contains("127.0.0.1"))
        }
    }

    @Test
    fun reportsNilAsMissingWithoutConnectionFailure() {
        TinyRespRedis(emptyMap()).use { redis ->
            val source = SocketRedisBinaryDataSource(SocketRedisConfig(host = "127.0.0.1", port = redis.port))

            assertNull(source.read("missing"))

            assertTrue(source.diagnostics().connected)
            assertEquals(0, source.diagnostics().keyCount)
        }
    }

    @Test
    fun providerBoundsSlowRemoteReadsWithSnapshotDeadline() {
        TinyRespRedis(
            mapOf(VehicleRedisKeys.SPEED to SimulatedVehicleRedisFixtures.speed(36.8f)),
            responseDelayMs = 500L
        ).use { redis ->
            val provider = RedisVehicleSnapshotProvider(
                dataSource = SocketRedisBinaryDataSource(SocketRedisConfig(host = "127.0.0.1", port = redis.port, timeoutMs = 1_000)),
                keys = listOf(VehicleRedisKeys.SPEED, VehicleRedisKeys.BATTERY),
                snapshotDeadlineMs = 120L
            )

            val started = System.currentTimeMillis()
            val snapshot = provider.readSnapshot()
            val elapsed = System.currentTimeMillis() - started

            assertTrue("elapsed=$elapsed", elapsed < 700L)
            assertFalse(snapshot.keyStatuses.getValue(VehicleRedisKeys.SPEED).decoded)
            assertEquals("snapshot_deadline_exceeded", snapshot.keyStatuses.getValue(VehicleRedisKeys.BATTERY).error)
        }
    }

    @Test
    fun providerIsolatesRemoteConnectionFailurePerKey() {
        val source = SocketRedisBinaryDataSource(SocketRedisConfig(host = "127.0.0.1", port = freeClosedPort(), timeoutMs = 100))

        val snapshot = RedisVehicleSnapshotProvider(source, keys = listOf(VehicleRedisKeys.SPEED)).readSnapshot()

        assertFalse(snapshot.diagnostics.connected)
        assertFalse(snapshot.keyStatuses.getValue(VehicleRedisKeys.SPEED).decoded)
        assertTrue(snapshot.keyStatuses.getValue(VehicleRedisKeys.SPEED).error!!.isNotBlank())
    }

    private fun freeClosedPort(): Int = ServerSocket(0).use { it.localPort }

    private class TinyRespRedis(
        initial: Map<String, ByteArray>,
        private val responseDelayMs: Long = 0L
    ) : AutoCloseable {
        private val server = ServerSocket(0)
        private val running = AtomicBoolean(true)
        private val state = initial.toMutableMap()
        val port: Int = server.localPort
        private val worker = thread(name = "tiny-resp-redis-test", isDaemon = true) {
            while (running.get()) {
                runCatching { server.accept() }
                    .onSuccess { socket -> thread(name = "tiny-resp-redis-client", isDaemon = true) { handle(socket) } }
            }
        }

        private fun handle(socket: Socket) {
            socket.use { client ->
                val input = BufferedInputStream(client.getInputStream())
                val output = client.getOutputStream()
                while (running.get()) {
                    val command = runCatching { readCommand(input) }.getOrNull() ?: return
                    when (command.firstOrNull()?.uppercase()) {
                        "AUTH", "SELECT" -> output.writeSimple("OK")
                        "GET" -> {
                            val key = command.getOrNull(1) ?: ""
                            if (responseDelayMs > 0L) Thread.sleep(responseDelayMs)
                            val value = state[key]
                            if (value == null) output.write("$-1\r\n".toByteArray(StandardCharsets.UTF_8)) else output.writeBulk(value)
                        }
                        else -> output.writeError("unsupported")
                    }
                    output.flush()
                }
            }
        }

        private fun readCommand(input: BufferedInputStream): List<String>? {
            val type = input.read()
            if (type < 0) return null
            require(type == '*'.code)
            val count = readLine(input).toInt()
            return List(count) {
                require(input.read() == '$'.code)
                val length = readLine(input).toInt()
                val bytes = input.readExactly(length)
                require(input.read() == '\r'.code)
                require(input.read() == '\n'.code)
                String(bytes, StandardCharsets.UTF_8)
            }
        }

        private fun readLine(input: BufferedInputStream): String {
            val out = ByteArrayOutputStream()
            while (true) {
                val b = input.read()
                if (b < 0) error("closed")
                if (b == '\r'.code) {
                    require(input.read() == '\n'.code)
                    return out.toString(StandardCharsets.UTF_8.name())
                }
                out.write(b)
            }
        }

        override fun close() {
            running.set(false)
            runCatching { server.close() }
            worker.join(500)
        }
    }
}

private fun java.io.OutputStream.writeSimple(text: String) {
    write("+$text\r\n".toByteArray(StandardCharsets.UTF_8))
}

private fun java.io.OutputStream.writeError(text: String) {
    write("-$text\r\n".toByteArray(StandardCharsets.UTF_8))
}

private fun java.io.OutputStream.writeBulk(bytes: ByteArray) {
    write("$${bytes.size}\r\n".toByteArray(StandardCharsets.UTF_8))
    write(bytes)
    write("\r\n".toByteArray(StandardCharsets.UTF_8))
}

private fun BufferedInputStream.readExactly(length: Int): ByteArray {
    val out = ByteArray(length)
    var offset = 0
    while (offset < length) {
        val read = read(out, offset, length - offset)
        if (read < 0) error("truncated")
        offset += read
    }
    return out
}

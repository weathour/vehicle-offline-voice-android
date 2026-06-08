package com.company.vehiclevoice.data.readonly

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

internal data class ProtoField(
    val number: Int,
    val wireType: Int,
    val varint: Long? = null,
    val fixed32: Int? = null,
    val fixed64: Long? = null,
    val bytes: ByteArray? = null
)

internal object ProtoWire {
    private const val WIRE_VARINT = 0
    private const val WIRE_FIXED64 = 1
    private const val WIRE_LENGTH_DELIMITED = 2
    private const val WIRE_FIXED32 = 5

    fun decode(payload: ByteArray): List<ProtoField> {
        val reader = Reader(payload)
        val fields = mutableListOf<ProtoField>()
        while (!reader.isAtEnd()) {
            val tag = reader.readVarint()
            val number = (tag ushr 3).toInt()
            val wireType = (tag and 0x07).toInt()
            require(number > 0) { "invalid protobuf field number: $number" }
            fields += when (wireType) {
                WIRE_VARINT -> ProtoField(number, wireType, varint = reader.readVarint())
                WIRE_FIXED64 -> ProtoField(number, wireType, fixed64 = reader.readFixed64())
                WIRE_LENGTH_DELIMITED -> ProtoField(number, wireType, bytes = reader.readBytes(reader.readVarint().toInt()))
                WIRE_FIXED32 -> ProtoField(number, wireType, fixed32 = reader.readFixed32())
                else -> error("unsupported protobuf wire type: $wireType")
            }
        }
        return fields
    }

    fun build(block: Builder.() -> Unit): ByteArray = Builder().apply(block).toByteArray()

    class Builder {
        private val out = ByteArrayOutputStream()

        fun uint32(number: Int, value: Int) = varint(number, value.toLong())
        fun int32(number: Int, value: Int) = varint(number, value.toLong())
        fun enum(number: Int, value: Int) = varint(number, value.toLong())

        fun varint(number: Int, value: Long) {
            writeTag(number, WIRE_VARINT)
            writeVarint(value)
        }

        fun float(number: Int, value: Float) {
            writeTag(number, WIRE_FIXED32)
            writeFixed32(java.lang.Float.floatToIntBits(value))
        }

        fun double(number: Int, value: Double) {
            writeTag(number, WIRE_FIXED64)
            writeFixed64(java.lang.Double.doubleToLongBits(value))
        }

        fun string(number: Int, value: String) {
            bytes(number, value.toByteArray(StandardCharsets.UTF_8))
        }

        fun message(number: Int, payload: ByteArray) = bytes(number, payload)

        fun bytes(number: Int, payload: ByteArray) {
            writeTag(number, WIRE_LENGTH_DELIMITED)
            writeVarint(payload.size.toLong())
            out.write(payload)
        }

        fun toByteArray(): ByteArray = out.toByteArray()

        private fun writeTag(number: Int, wireType: Int) {
            require(number > 0) { "field number must be positive" }
            writeVarint(((number shl 3) or wireType).toLong())
        }

        private fun writeVarint(raw: Long) {
            var value = raw
            while (true) {
                if ((value and 0x7f.inv().toLong()) == 0L) {
                    out.write(value.toInt())
                    return
                }
                out.write(((value and 0x7f) or 0x80).toInt())
                value = value ushr 7
            }
        }

        private fun writeFixed32(value: Int) {
            out.write(value and 0xff)
            out.write((value ushr 8) and 0xff)
            out.write((value ushr 16) and 0xff)
            out.write((value ushr 24) and 0xff)
        }

        private fun writeFixed64(value: Long) {
            repeat(8) { shift -> out.write(((value ushr (shift * 8)) and 0xff).toInt()) }
        }
    }

    private class Reader(private val payload: ByteArray) {
        private var offset = 0

        fun isAtEnd(): Boolean = offset >= payload.size

        fun readVarint(): Long {
            var shift = 0
            var result = 0L
            while (shift < 64) {
                val b = readByte()
                result = result or ((b and 0x7f).toLong() shl shift)
                if ((b and 0x80) == 0) return result
                shift += 7
            }
            error("malformed protobuf varint")
        }

        fun readFixed32(): Int {
            requireRemaining(4)
            var result = 0
            repeat(4) { shift -> result = result or (readByte() shl (shift * 8)) }
            return result
        }

        fun readFixed64(): Long {
            requireRemaining(8)
            var result = 0L
            repeat(8) { shift -> result = result or (readByte().toLong() shl (shift * 8)) }
            return result
        }

        fun readBytes(size: Int): ByteArray {
            require(size >= 0) { "negative length-delimited protobuf size" }
            requireRemaining(size)
            return payload.copyOfRange(offset, offset + size).also { offset += size }
        }

        private fun readByte(): Int {
            requireRemaining(1)
            return payload[offset++].toInt() and 0xff
        }

        private fun requireRemaining(size: Int) {
            require(offset + size <= payload.size) { "truncated protobuf payload" }
        }
    }
}

internal fun List<ProtoField>.lastVarint(number: Int): Int? =
    lastOrNull { it.number == number && it.varint != null }?.varint?.toInt()

internal fun List<ProtoField>.lastFloat(number: Int): Float? =
    lastOrNull { it.number == number && it.fixed32 != null }?.fixed32?.let { java.lang.Float.intBitsToFloat(it) }

internal fun List<ProtoField>.lastDouble(number: Int): Double? =
    lastOrNull { it.number == number && it.fixed64 != null }?.fixed64?.let { java.lang.Double.longBitsToDouble(it) }

internal fun List<ProtoField>.lastString(number: Int): String? =
    lastOrNull { it.number == number && it.bytes != null }?.bytes?.let { String(it, StandardCharsets.UTF_8) }

internal fun List<ProtoField>.messages(number: Int): List<ByteArray> =
    filter { it.number == number && it.bytes != null }.map { it.bytes!!.copyOf() }

package com.company.vehiclevoice.audio

fun List<PcmFrame>.toLittleEndianPcm16Bytes(): ByteArray {
    val totalSamples = sumOf { it.samples.size }
    val bytes = ByteArray(totalSamples * 2)
    var offset = 0
    for (frame in this) {
        for (sample in frame.samples) {
            val value = sample.toInt()
            bytes[offset++] = (value and 0xff).toByte()
            bytes[offset++] = ((value ushr 8) and 0xff).toByte()
        }
    }
    return bytes
}

fun PcmFrame.toLittleEndianPcm16Bytes(): ByteArray = listOf(this).toLittleEndianPcm16Bytes()

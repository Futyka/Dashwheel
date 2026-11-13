package com.openauto.dash

import java.util.Locale

/**
 * A received classic-CAN frame from a LAWICEL/SLCAN USB adapter.
 * No CAN frame transmission is implemented here.
 */
internal data class SlcanFrame(
    val id: Long,
    val extended: Boolean,
    val remote: Boolean,
    val data: ByteArray,
    val dlc: Int
) {
    fun hexData(): String = data.joinToString(" ") { "%02X".format(Locale.US, it.toInt() and 0xFF) }
    fun format(): String =
        "%0${if (extended) 8 else 3}X [%d] %s%s".format(Locale.US, id, dlc, hexData(), if (remote) " RTR" else "")
}

/** Defensive parser: never mistake firmware responses or error flags for CAN values. */
internal object SlcanFrames {
    fun parse(raw: String): SlcanFrame? {
        val s = raw.trimEnd('\r', '\n')
        val type = s.firstOrNull() ?: return null
        if (type !in "tTrR") return null
        val extended = type == 'T' || type == 'R'
        val remote = type == 'R' || type == 'r'
        val idLength = if (extended) 8 else 3
        val headerLength = 2 + idLength
        if (s.length < headerLength) return null
        val id = s.substring(1, idLength + 1).toLongOrNull(16) ?: return null
        if (id > if (extended) 0x1FFFFFFFL else 0x7FFL) return null
        val dlc = s.substring(idLength + 1, headerLength).toIntOrNull(16) ?: return null
        if (dlc !in 0..8) return null
        val payloadEnd = headerLength + if (remote) 0 else dlc * 2
        if (s.length < payloadEnd) return null
        val data = ByteArray(if (remote) 0 else dlc)
        for (i in data.indices) {
            val byte = s.substring(headerLength + i * 2, headerLength + i * 2 + 2).toIntOrNull(16) ?: return null
            data[i] = byte.toByte()
        }
        // Timestamps, if enabled, are four optional hexadecimal digits.
        val trailing = s.substring(payloadEnd)
        if (trailing.isNotEmpty() && (trailing.length != 4 || trailing.any { it.digitToIntOrNull(16) == null })) return null
        return SlcanFrame(id, extended, remote, data, dlc)
    }
}

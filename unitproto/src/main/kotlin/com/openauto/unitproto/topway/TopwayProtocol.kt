package com.openauto.unitproto.topway

/**
 * The Topway TS firmware's MCU link, as its car app (com.tw.car 880_0000_260911,
 * a TS10.2.2 unit's copy) uses it: the framework's `android.tw.john.TWUtil`,
 * opened on a list of channels (`open(short[])`, 0 for done), started, and
 * handed a Handler (`addHandler(tag, handler)`). Each message is one channel
 * ([android.os.Message.what]), with [android.os.Message.arg1] and, for the
 * canbox's, its frame's bytes in [android.os.Message.obj].
 *
 * Read from the car app's bytecode, not from Topway: what is not named here
 * is not known. Dashwheel only listens; it never writes to the MCU.
 */
object TopwayProtocol {
    /** The framework class every Topway app talks to the MCU through. */
    const val TWUTIL_CLASS = "android.tw.john.TWUtil"

    /** Reverse: arg1 1 in reverse, 0 out of it (the car app's CarService). */
    const val CH_REVERSE = 0x20B
    /** Handled by the car app exactly as [CH_REVERSE] (another source of the same news). */
    const val CH_REVERSE_ALT = 0x9F1C
    /** The canbox: arg1 the frame's type, obj its bytes (0x40 and 0x21 carry the Jeep's settings). */
    const val CH_CANBOX = 0x501
    /** The car the canbox was set up for: obj's byte 12 tells which. */
    const val CH_CAR_TYPE = 0x10B

    /** What Dashwheel opens: only what it reads. */
    val LISTENED = shortArrayOf(CH_REVERSE.toShort(), CH_REVERSE_ALT.toShort(), CH_CANBOX.toShort(), CH_CAR_TYPE.toShort())

    /** Finder keys: one byte value per channel and frame type ("tw:1281:64[]"), sliced into bytes and bits. */
    const val PREFIX = "tw"

    /** Whether [arg1] of a reverse channel says reverse (true), not reverse (false), or nothing the car app reads (null). */
    fun reverse(arg1: Int): Boolean? = when (arg1) {
        1 -> true
        0 -> false
        else -> null
    }

    fun frameKey(channel: Int, type: Int): String = "$PREFIX:$channel:$type[]"

    /** The bytes as the finder reads byte values: two hex digits each, spaced. */
    fun hex(bytes: ByteArray): String = bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    /** The channel is a reverse one ([CH_REVERSE], [CH_REVERSE_ALT]). */
    fun isReverse(channel: Int): Boolean = channel == CH_REVERSE || channel == CH_REVERSE_ALT

    /** The channels whose bytes go to the finder. */
    fun carriesFrames(channel: Int): Boolean = channel == CH_CANBOX || channel == CH_CAR_TYPE
}

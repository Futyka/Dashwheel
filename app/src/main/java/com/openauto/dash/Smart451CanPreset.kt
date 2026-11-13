package com.openauto.dash

/**
 * Vehicle-specific candidate parameters from field observations.
 *
 * These are hints for a future in-app CANable2 adapter settings / My readings
 * integration, NOT an active connection or a verified fuel percentage decoder.
 */
internal object Smart451CanPreset {
    const val BITRATE_BITS_PER_SECOND = 500_000
    const val SLCAN_BITRATE_COMMAND = "S6"
    const val FUEL_CANDIDATE_ID = 0x408L
    const val FUEL_EXTENDED_ID = false

    /**
     * Fuel level has not yet been mapped to payload bytes and a scaling formula.
     * Never display a guessed value. Allow a verified formula to be provided
     * by the user's My readings configuration when that integration is wired.
     */
    fun fuelPercent(frame: SlcanFrame, verifiedFormula: String?): Double? {
        if (verifiedFormula.isNullOrBlank()) return null
        val value = SlcanReadingDecoder.value(frame, FUEL_CANDIDATE_ID, FUEL_EXTENDED_ID, verifiedFormula)
        return value?.takeIf { it.isFinite() && it in 0.0..100.0 }
    }
}

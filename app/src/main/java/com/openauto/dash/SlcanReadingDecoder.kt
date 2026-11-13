package com.openauto.dash

/**
 * A user-mapped CAN value, not a guessed PID. Use the existing Dashwheel
 * formula grammar (A..H refer to raw CAN payload bytes).
 */
internal object SlcanReadingDecoder {
    private val noOtherValues = object : Formula.Names {
        override fun known(name: String) = false
        override fun value(name: String): Double? = null
    }

    /** Returns null until a matching data frame and a valid formula are received. */
    fun value(frame: SlcanFrame, canId: Long, extended: Boolean, formula: String): Double? {
        if (frame.id != canId || frame.extended != extended || frame.remote) return null
        return Formula.evaluate(formula, frame.data.map { it.toInt() and 0xFF }, noOtherValues).value
    }
}

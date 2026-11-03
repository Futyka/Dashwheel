package com.openauto.unitproto.nwd

/*
 * The NWD (Nowadays) firmware: K4811 and the like, and the Qualcomm Bengal
 * units of issue #122. Its CAN SDK (com.nwd.can.sdk) keeps what it decodes to
 * its own apps; what reaches every app, as the open K4811 launcher reads it
 * (github.com/garagemdomadeira/gdm-launcher-k4811, HomeActivity.java and
 * NwdSettings.java): the battery voltage on request, and the MCU's headlight
 * wire in Settings.System, and the outside temperature the CAN app writes to
 * Settings.System (decompiled from the Car_Info APK of issue #122:
 * AbsSender.sendOutsideTemperature -> CanConfigUtil.setOutTemp). Unconfirmed
 * on a unit; doors, reverse and speed are still to be found.
 */
object NwdProtocol {
    /** Ask for the voltage (no extras); the firmware answers with [ACTION_VOLTAGE], or not at all on a car without CAN. */
    const val ACTION_QUERY_VOLTAGE = "com.nwd.action.ACTION_QUERY_CAN_BATTERY_VOLTAGE"
    /** The answer: int extra [EXTRA_VOLTAGE] = volts x 10, 0 when the firmware has no value. */
    const val ACTION_VOLTAGE = "com.nwd.action.ACTION_CAN_BATTERY_VOLTAGE"
    const val EXTRA_VOLTAGE = "extra_voltage_value"
    /** The stock launcher asks every 10 s. */
    const val QUERY_PERIOD_MS = 10_000L

    /** The firmware's own, told in the launcher's event log (UnitLauncher) and worth the Signal Finder's raw view. */
    const val ACTION_APP_IN_OUT = "com.nwd.action.ACTION_APP_IN_OUT"
    const val ACTION_CHANGE_SOURCE = "com.nwd.action.ACTION_CHANGE_SOURCE"
    /** The CAN app also tells the status bar each new temperature text, extra "extra_string". */
    const val ACTION_STATUSBAR_STRING = "com.nwd.android.ACTION_STATUSBAR_STRING"
    val BROADCASTS = setOf(ACTION_VOLTAGE, ACTION_APP_IN_OUT, ACTION_CHANGE_SOURCE, ACTION_STATUSBAR_STRING)

    /** Settings.System: the MCU's input wires as bits, [ILL_BIT] the headlights. */
    const val SETTING_MCU_INPUTS = "mcu_no_source_device_state"
    /** Settings.System: the CAN app's outside temperature as its status-bar text ("23.5℃" or "74℉"), absent until the car sends one. */
    const val SETTING_OUT_TEMP = "car_config_out_temp"
    const val ILL_BIT = 2

    /** The volts from [EXTRA_VOLTAGE]'s raw value; null for the firmware's "no value". */
    fun volts(raw: Int): Double? = (Math.abs(raw) / 10.0).takeIf { it > 0.0 }

    fun headlightsOn(mcuInputs: Int): Boolean = (mcuInputs and ILL_BIT) != 0
}

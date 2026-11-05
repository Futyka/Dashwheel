package com.openauto.dash

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.DateFormat
import java.util.TimeZone

/**
 * The unit's time zone, kept current. A date format takes the zone of the
 * moment it is made and keeps it, and the dashboard's are made once and stay
 * for weeks (the unit sleeps, it is not switched off). When the zone changed
 * after that (the unit setting it from GPS or the network after a start, a
 * zone set as a plain offset moved at the end of summer time), every clock
 * stayed an hour out. Formats are made again, in the new zone, when it changes.
 */
internal object ZoneWatch {
    private const val TAG = "ZoneWatch"

    private val _zone = MutableStateFlow(TimeZone.getDefault().id)
    val zone: StateFlow<String> = _zone.asStateFlow()

    fun init(context: Context) {
        val app = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = check()
        }
        val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED).apply { addAction(Intent.ACTION_TIME_CHANGED) }
        // System broadcasts: registered from the code, they need no export.
        runCatching { ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED) }
            .onFailure { Log.w(TAG, "no time zone broadcasts", it) }
        check()
    }

    /**
     * Reads the system's zone again. Android hands the app the new zone with its
     * broadcast; reading it here too covers firmwares that change it while the
     * app is asleep and say nothing (called at each return to the dashboard).
     */
    fun check() {
        TimeZone.setDefault(null)
        val id = TimeZone.getDefault().id
        if (id != _zone.value) {
            Log.i(TAG, "time zone ${_zone.value} -> $id")
            _zone.value = id
        }
    }
}

/** The unit's time zone, read again when it changes ([ZoneWatch]): a key for remembered date formats. */
@Composable
internal fun rememberZone(): TimeZone {
    val id by ZoneWatch.zone.collectAsState()
    return remember(id) { TimeZone.getTimeZone(id) }
}

/** This format, in [zone]: for a format remembered with the zone as a key. */
internal fun <T : DateFormat> T.inZone(zone: TimeZone): T = apply { timeZone = zone }

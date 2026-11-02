package com.openauto.dash

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.openauto.dash.link.FuelFills
import com.openauto.dash.vehicle.K
import com.openauto.dash.vehicle.Vehicle
import com.openauto.dash.vehicle.holdAll
import com.openauto.dash.vehicle.rememberGauge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

/*
 * The Consumption widget: what the car burns now, since the last fill-up and
 * on average. "Now" is the car's own instant figure where the CAN box gives
 * one, else the adapter's fuel rate over the speed (the rate itself, L/h, at
 * a standstill). The averages need the litres actually burned, which no feed
 * kept: [FuelUsed] adds the fuel rate up second by second, since the last
 * refuel in the fuel log and since the count began, while the widget is on a
 * dashboard; the kilometres are the GPS trip's, counted as the trip computer
 * counts them ([SinceFill]). The refuel-to-refuel figure of the fuel log wins
 * for the overall average once there is one.
 */

/** Litres added up from the fuel rate: since the refuel at [fillAt], and since the count began, with its km ([total]). */
internal data class FuelUsedState(
    val fillAt: Long? = null,
    val sinceFillL: Double = 0.0,
    val totalL: Double = 0.0,
    val total: SinceFillState = SinceFillState()
)

internal object FuelUsed {
    private const val PREFS = "fuel_used"
    private const val TICK_MS = 1_000L
    /** Written every this many litres: a power cut loses at most this much. */
    private const val SAVE_EVERY_L = 0.1

    // Main thread, where the GPS feed and the fuel log publish.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var appContext: Context? = null
    private var savedL = 0.0

    private val _state = MutableStateFlow(FuelUsedState())
    val state: StateFlow<FuelUsedState> = _state.asStateFlow()

    /** Starts once for the process, after [FuelLog] (the refuels read); main thread. */
    fun start(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        prefs(app).getString("state", null)?.let { runCatching { fromJson(JSONObject(it)) }.getOrNull() }?.let { _state.value = it }
        savedL = _state.value.totalL
        scope.launch {
            FuelLog.fills.collect { fills ->
                val at = fills.firstOrNull()?.at
                if (at != null && at != _state.value.fillAt) update(_state.value.copy(fillAt = at, sinceFillL = 0.0))
            }
        }
        // The demo's trip is not driven: the real one comes back unchanged when it ends.
        scope.launch { LocationFeed.trip.collect { trip -> if (!DemoMode.isOn) update(_state.value.copy(total = SinceFillRules.follow(_state.value.total, trip))) } }
        // The fuel rate is asked of the car only while a Consumption tile is somewhere to show it.
        scope.launch { DashboardStore.placedKinds.map { BuiltinKind.CONSUMPTION in it }.distinctUntilChanged().collectLatest { if (it) count() } }
    }

    /** What the engine burns now, L/h: the adapter's fuel rate, else the car's own L/100 km over its speed. */
    private fun flowLh(): Double? = Vehicle.bus.now(K.FUEL_FLOW)
        ?: Vehicle.bus.now(K.FUEL_CONSUMPTION)?.let { l100 -> Vehicle.bus.now(K.SPEED)?.let { l100 * it / 100 } }

    private suspend fun count() {
        val hold = Vehicle.bus.holdAll(listOf(K.FUEL_FLOW, K.FUEL_CONSUMPTION, K.SPEED))
        try {
            while (true) {
                delay(TICK_MS)
                if (DemoMode.isOn) continue
                val l = (flowLh() ?: continue) * TICK_MS / 3_600_000.0
                val s = _state.value
                update(s.copy(sinceFillL = s.sinceFillL + l, totalL = s.totalL + l))
            }
        } finally {
            hold.release()
        }
    }

    private fun update(next: FuelUsedState) {
        val before = _state.value
        if (next == before) return
        _state.value = next
        val app = appContext ?: return
        if (next.fillAt != before.fillAt || next.total.tripStartedAt != before.total.tripStartedAt || abs(next.totalL - savedL) >= SAVE_EVERY_L) {
            savedL = next.totalL
            prefs(app).edit().putString("state", toJson(next).toString()).apply()
        }
    }

    private fun toJson(s: FuelUsedState) = JSONObject().apply {
        s.fillAt?.let { put("fill", it) }
        put("since_l", s.sinceFillL).put("total_l", s.totalL)
        put("m", s.total.distanceM)
        s.total.tripStartedAt?.let { put("trip", it) }
        put("trip_m", s.total.tripM)
    }

    private fun fromJson(o: JSONObject) = FuelUsedState(
        fillAt = if (o.has("fill")) o.getLong("fill") else null,
        sinceFillL = o.getDouble("since_l"), totalL = o.getDouble("total_l"),
        total = SinceFillState(
            distanceM = o.getDouble("m"),
            tripStartedAt = if (o.has("trip")) o.getLong("trip") else null,
            tripM = o.optDouble("trip_m", 0.0)
        )
    )

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** The three figures as shown: [now] with its [nowUnit] (L/h when the car stands), the averages with theirs. */
internal class ConsumptionFigures(val now: String, val nowUnit: String, val sinceFill: String, val average: String)

@Composable
internal fun rememberConsumptionFigures(): ConsumptionFigures {
    val u = LocalUnits.current
    val carL100 = rememberGauge(K.FUEL_CONSUMPTION)
    val flow = rememberGauge(K.FUEL_FLOW)
    val kmh = rememberGauge(K.SPEED)
    val used by FuelUsed.state.collectAsState()
    val fills by FuelLog.fills.collectAsState()
    val trip by LocationFeed.trip.collectAsState()
    val since = rememberSinceFill(trip)
    val demo by DemoMode.active.collectAsState()
    val rate = rememberFuelRate()
    val locale = Locale.getDefault()
    // Under 5 km/h the L/100 km figure runs away: the rate itself is shown then.
    val l100 = carL100 ?: flow?.let { f -> kmh?.takeIf { it >= 5 }?.let { f / it * 100 } }
    val now = when {
        l100 != null -> (u.economy(l100)?.let { String.format(locale, "%.1f", it) } ?: "--") to u.economyUnit
        flow != null -> String.format(locale, "%.1f", flow) to "L/h"
        else -> "--" to u.economyUnit
    }
    // Under a kilometre the averages would read off the first few drops; the demo shows the car's usual figure.
    val sinceFill = if (demo) rate.use else used.sinceFillL.takeIf { it > 0 && since.km >= 1 }?.let { it / since.km * 100 }
    val average = FuelFills.litersPer100(fills)
        ?: if (demo) rate.use else used.totalL.takeIf { it > 0 && used.total.km >= 1 }?.let { it / used.total.km * 100 }
    return ConsumptionFigures(now.first, now.second, sinceFill?.let { u.economyText(it) } ?: "--", average?.let { u.economyText(it) } ?: "--")
}

/** What the car burns: now, big; since the last fill-up and on average beside it. */
@Composable
internal fun ConsumptionCard(modifier: Modifier = Modifier) {
    val f = rememberConsumptionFigures()
    Card(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize().padding(DashSpace.Lg)) {
            TileHeader(stringResource(R.string.widgets_consumption))
            Row(modifier = Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1.2f)) {
                    HeroNumber(text = f.now, size = 44)
                    Text(f.nowUnit, color = DashColors.Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DashSpace.Sm)) {
                    StatBlock(stringResource(R.string.info_trip_since_fill), f.sinceFill)
                    StatBlock(stringResource(R.string.info_trip_average), f.average)
                }
            }
        }
    }
}

@Composable
internal fun consumptionFace(): WidgetFace {
    val f = rememberConsumptionFigures()
    return WidgetFace(
        icon = Icons.Filled.LocalGasStation,
        title = BuiltinKind.CONSUMPTION.label,
        value = f.now, unit = f.nowUnit,
        stats = listOf(
            FaceStat(stringResource(R.string.info_trip_since_fill), f.sinceFill),
            FaceStat(stringResource(R.string.info_trip_average), f.average)
        )
    )
}

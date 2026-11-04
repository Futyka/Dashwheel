package com.openauto.dash.unit

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.util.Log
import com.openauto.dash.Cap
import com.openauto.dash.DebugLog
import com.openauto.dash.HiddenApi
import com.openauto.dash.UnitPlatform
import com.openauto.dash.VehicleBusSource
import com.openauto.dash.vehicle.K
import com.openauto.dash.vehicle.Publisher
import com.openauto.unitproto.topway.TopwayProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.lang.reflect.Method

/**
 * The Topway TS firmware's MCU link ([TopwayProtocol]), read-only: the reverse
 * the car app hears, and the canbox's frames for the Signal Finder to learn
 * the doors, the lights and the rest from. Through the framework's own TWUtil,
 * the way the firmware's Bluetooth, EQ and car apps each open it; a unit that
 * keeps it from Dashwheel (a refused open, a hidden-API block) leaves a line
 * in the debug log and the bus silent. Unconfirmed on a unit.
 */
internal object TopwayBus : VehicleBusSource {
    private const val TAG = "TopwayBus"
    private const val HANDLER_TAG = "Dashwheel"

    private val _listening = MutableStateFlow(false)
    override val listening: StateFlow<Boolean> = _listening

    /** The canbox speaks while the car is on; the reverse channel only when it changes. */
    override val silenceMs: Long = 20_000L

    private var out: Publisher? = null
    private var raw: ((String, String) -> Unit)? = null
    private var tw: Any? = null
    private var thread: HandlerThread? = null
    private var frames = 0

    override fun start(context: Context, publisher: Publisher, raw: (String, String) -> Unit) {
        if (out != null) return
        out = publisher
        this.raw = raw
        val app = context.applicationContext
        val t = HandlerThread(TAG).also { it.start() }
        thread = t
        val handler = Handler(t.looper) { msg -> onMessage(msg); true }
        runCatching {
            val cls = Class.forName(TopwayProtocol.TWUTIL_CLASS)
            val util = runCatching { cls.getConstructor().newInstance() }.getOrElse { HiddenApi.newInstance(cls) }
            val opened = method(cls, "open", ShortArray::class.java).invoke(util, TopwayProtocol.LISTENED) as Int
            if (opened != 0) error("open answered $opened")
            method(cls, "start").invoke(util)
            method(cls, "addHandler", String::class.java, Handler::class.java).invoke(util, HANDLER_TAG, handler)
            tw = util
            _listening.value = true
            Log.i(TAG, "listening to the MCU through TWUtil")
            DebugLog.note(app, "Topway MCU: listening through TWUtil (reverse, canbox frames)")
        }.onFailure {
            Log.w(TAG, "TWUtil is not there for Dashwheel", it)
            DebugLog.note(app, "Topway MCU: TWUtil refused (${it.cause ?: it})")
            t.quitSafely()
            thread = null
        }
    }

    override fun stop() {
        val util = tw
        tw = null
        if (util != null) runCatching {
            val cls = util.javaClass
            method(cls, "removeHandler", String::class.java).invoke(util, HANDLER_TAG)
            method(cls, "stop").invoke(util)
            method(cls, "close").invoke(util)
        }
        thread?.quitSafely()
        thread = null
        out = null
        raw = null
        _listening.value = false
    }

    /** Public members first; the hidden-API way only when Android keeps them from Dashwheel. */
    private fun method(cls: Class<*>, name: String, vararg types: Class<*>): Method =
        runCatching { cls.getMethod(name, *types) }.getOrElse { HiddenApi.getDeclaredMethod(cls, name, *types) }

    private fun onMessage(msg: Message) {
        val o = out ?: return
        when {
            TopwayProtocol.isReverse(msg.what) -> {
                val on = TopwayProtocol.reverse(msg.arg1) ?: return
                UnitPlatform.markUsed(Cap.REVERSE)
                o.put(K.REVERSE, on)
            }
            TopwayProtocol.carriesFrames(msg.what) -> {
                val bytes = msg.obj as? ByteArray ?: return
                UnitPlatform.markUsed(Cap.VEHICLE_BUS)
                if (frames++ == 0) Log.i(TAG, "canbox frames are coming (channel ${msg.what}, type ${msg.arg1}, ${bytes.size} bytes)")
                raw?.invoke(TopwayProtocol.frameKey(msg.what, msg.arg1), TopwayProtocol.hex(bytes))
            }
        }
    }
}

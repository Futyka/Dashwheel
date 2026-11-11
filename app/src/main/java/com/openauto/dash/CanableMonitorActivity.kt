package com.openauto.dash

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.graphics.Typeface
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import android.hardware.usb.UsbManager
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Experimental, read-only CANable2 SLCAN monitor.
 * CAN TX frames are never sent. The CAN interface is opened in silent mode (M1).
 * This tool does NOT replace ELM327 OBD polling or presume that any ID is fuel level.
 */
class CanableMonitorActivity : Activity() {
    private val scope = CoroutineScope(Dispatchers.Main)
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null
    @Volatile private var port: UsbSerialPort? = null
    private lateinit var status: TextView
    private lateinit var frames: TextView
    private val recent = ArrayDeque<String>()
    private var count = 0
    private var lastUiAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 16, 22, 12)
        }
        val title = TextView(this).apply { text = "CANable2 · SLCAN monitor"; textSize = 23f }
        status = TextView(this).apply { text = "Stopped — select the verified CAN bitrate"; textSize = 16f }
        val note = TextView(this).apply {
            text = "Receive only (silent M1). Connect CAN-H/CAN-L/GND correctly. The bit rate must match the vehicle bus. Run only while parked."
            textSize = 13f
        }
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val b250 = Button(this).apply { text = "250k"; setOnClickListener { start("S5") } }
        val b500 = Button(this).apply { text = "500k"; setOnClickListener { start("S6") } }
        val stop = Button(this).apply { text = "Stop"; setOnClickListener { stop() } }
        buttons.addView(b250, LinearLayout.LayoutParams(0, -2, 1f))
        buttons.addView(b500, LinearLayout.LayoutParams(0, -2, 1f))
        buttons.addView(stop, LinearLayout.LayoutParams(0, -2, 1f))
        frames = TextView(this).apply {
            text = "CAN frames will appear here, in hexadecimal."
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply { addView(frames) }
        root.addView(title)
        root.addView(status)
        root.addView(note)
        root.addView(buttons)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun start(speed: String) {
        if (running.get()) return
        scope.launch {
            val usb = getSystemService(Context.USB_SERVICE) as UsbManager
            // The driver already selected this USB device in Dashwheel settings.
            val adapter = UsbObd.attached(this@CanableMonitorActivity)
                .firstOrNull { it.label.contains("CANable", true) }
            if (adapter == null) {
                status.text = "No selected CANable2 USB serial adapter"
                return@launch
            }
            if (!UsbObd.allow(this@CanableMonitorActivity, adapter)) {
                status.text = "USB permission denied"
                return@launch
            }
            val driver = UsbSerialProber.getDefaultProber().probeDevice(adapter.device)
            val serial = driver?.ports?.firstOrNull()
            val connection = usb.openDevice(adapter.device)
            if (serial == null || connection == null) {
                status.text = "USB driver/device unavailable"
                return@launch
            }
            running.set(true)
            count = 0
            recent.clear()
            frames.text = ""
            worker = Thread {
                try {
                    serial.open(connection)
                    port = serial
                    serial.setParameters(115200, 8, 1, 0)
                    // Close any old channel; never issue a CAN transmit command.
                    serial.write("C\r".toByteArray(), 1000)
                    serial.write("V\r".toByteArray(), 1000)
                    val version = readFor(serial, 400)
                    // Firmware-specific silent mode, not a generic SLCAN acceptance mask.
                    serial.write("$speed\r".toByteArray(), 1000)
                    serial.write("M1\r".toByteArray(), 1000)
                    serial.write("O\r".toByteArray(), 1000)
                    runOnUiThread {
                        status.text = "CAN monitor · ${if (speed == "S6") "500k" else "250k"} · ${version.take(70)}"
                    }
                    val buf = ByteArray(4096)
                    val line = StringBuilder()
                    while (running.get()) {
                        val n = serial.read(buf, 200)
                        for (i in 0 until n) {
                            val c = (buf[i].toInt() and 255).toChar()
                            if (c == '\r' || c == '\n') {
                                if (line.isNotEmpty()) {
                                    SlcanFrames.parse(line.toString())?.let { frame ->
                                        count++
                                        recent.addLast(frame)
                                        if (recent.size > 80) recent.removeFirst()
                                        if (SystemClock.elapsedRealtime() - lastUiAt > 200) {
                                            val output = recent.joinToString("\n")
                                            val total = count
                                            runOnUiThread {
                                                if (running.get()) {
                                                    frames.text = output
                                                    status.text = "Silent ${if (speed == "S6") "500k" else "250k"} · $total frames"
                                                }
                                            }
                                            lastUiAt = SystemClock.elapsedRealtime()
                                        }
                                    }
                                    line.setLength(0)
                                }
                            } else if (line.length < 180) line.append(c)
                            else line.setLength(0)
                        }
                    }
                } catch (ex: Exception) {
                    runOnUiThread { status.text = "CANable2 error: ${ex.message}" }
                } finally {
                    runCatching { serial.write("C\r".toByteArray(), 300) }
                    runCatching { serial.close() }
                    runCatching { connection.close() }
                    port = null
                    running.set(false)
                }
            }.apply { name = "Canable2-read-only"; start() }
        }
    }

    private fun readFor(serial: UsbSerialPort, milliseconds: Long): String {
        val until = SystemClock.elapsedRealtime() + milliseconds
        val data = StringBuilder()
        val buffer = ByteArray(256)
        while (SystemClock.elapsedRealtime() < until) {
            val n = serial.read(buffer, 50)
            for (i in 0 until n) data.append((buffer[i].toInt() and 255).toChar())
        }
        return data.toString().trim()
    }

    private fun stop() {
        running.set(false)
        status.text = "Stopping CAN monitor"
    }

    override fun onDestroy() {
        stop()
        super.onDestroy()
    }
}

/** Parser for classic SLCAN receive frames, not a CAN signal decoder. */
internal object SlcanFrames {
    fun parse(line: String): String? {
        val type = line.firstOrNull() ?: return null
        if (type !in "tTrR") return null
        val extended = type == 'T' || type == 'R'
        val idChars = if (extended) 8 else 3
        if (line.length < 1 + idChars + 1) return null
        val id = line.substring(1, 1 + idChars).toLongOrNull(16) ?: return null
        if (id > if (extended) 0x1FFFFFFFL else 0x7FFL) return null
        val dlc = line.substring(1 + idChars, 2 + idChars).toIntOrNull(16) ?: return null
        if (dlc !in 0..8) return null
        val remote = type == 'r' || type == 'R'
        val start = 2 + idChars
        if (!remote && line.length < start + dlc * 2) return null
        val parts = mutableListOf<String>()
        if (!remote) {
            for (n in 0 until dlc) {
                val value = line.substring(start + n * 2, start + n * 2 + 2)
                if (value.toIntOrNull(16) == null) return null
                parts.add(value.uppercase(Locale.US))
            }
        }
        val bytes = parts.joinToString(" ")
        return "%0${idChars}X [%d] %s%s".format(Locale.US, id, dlc, bytes, if (remote) " RTR" else "")
    }
}

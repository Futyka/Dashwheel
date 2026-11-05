package com.openauto.dash

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import kotlin.system.exitProcess

/**
 * Lets Dashwheel use a USB device without Android's dialog. Android forgets a
 * "yes" in that dialog as soon as the device goes away, and on a head unit
 * that is at every start of the car: a tyre receiver wired in for good asked
 * again each morning. Where Dashwheel has a privileged shell (root, Shizuku,
 * the unit's internal ADB: [PrivilegedShell]), the shell gives the permission
 * itself, as the dialog would: `IUsbManager.grantDevicePermission` needs
 * MANAGE_USB, which the shell user and root hold and an app does not.
 *
 * Run by the privileged shell ([DockShell]) as
 * `CLASSPATH=<apk> app_process /system/bin com.openauto.dash.UsbGrant <device path> <uid>`.
 * Prints "granted" when it was given.
 */
object UsbGrant {
    private const val TAG = "UsbGrant"

    @JvmStatic
    fun main(args: Array<String>) {
        val name = args.getOrNull(0)
        val uid = args.getOrNull(1)?.toIntOrNull()
        if (name.isNullOrBlank() || uid == null) {
            System.err.println("UsbGrant: usage: <device path> <uid>")
            exitProcess(2)
        }
        try {
            val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
                .invoke(null, Context.USB_SERVICE) as? IBinder ?: error("no usb service")
            // The methods are looked up on the interface: the binder proxy's own class is private.
            val api = Class.forName("android.hardware.usb.IUsbManager")
            val service = Class.forName("android.hardware.usb.IUsbManager\$Stub")
                .getMethod("asInterface", IBinder::class.java).invoke(null, binder) ?: error("no usb service")
            val list = Bundle()
            api.getMethod("getDeviceList", Bundle::class.java).invoke(service, list)
            list.classLoader = UsbDevice::class.java.classLoader
            @Suppress("DEPRECATION")
            val device = list.keySet().map { list.get(it) }.filterIsInstance<UsbDevice>().firstOrNull { it.deviceName == name }
                ?: error("$name is not plugged in")
            api.getMethod("grantDevicePermission", UsbDevice::class.java, Int::class.javaPrimitiveType)
                .invoke(service, device, uid)
            println("granted")
            exitProcess(0)
        } catch (e: Throwable) {
            System.err.println("UsbGrant: ${e.cause ?: e}")
            exitProcess(1)
        }
    }

    /** The shell line that runs it. Pure, for the tests. */
    internal fun command(apk: String, deviceName: String, uid: Int): String =
        "CLASSPATH=$apk app_process /system/bin ${UsbGrant::class.java.name} '$deviceName' $uid"

    /** The device paths given or tried already in this process: one try each, not one every round. */
    private val tried = HashSet<String>()

    /**
     * Has the privileged shell let Dashwheel use [device], with no dialog;
     * true once Dashwheel may. Tried once per device path (a new plug-in gets
     * a new one), and only where a shell is known to be there: never on the
     * Play edition, a phone or an unrooted unit.
     */
    suspend fun silently(context: Context, device: UsbDevice): Boolean {
        val app = context.applicationContext
        val manager = app.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return false
        if (runCatching { manager.hasPermission(device) }.getOrDefault(false)) return true
        if (!PrivilegedShell.access.value.shell) return false
        if (!synchronized(tried) { tried.add(device.deviceName) }) return false
        val out = runCatching {
            DockShell.shell(app, command(app.applicationInfo.sourceDir, device.deviceName, app.applicationInfo.uid)).trim()
        }.getOrElse { "no shell: ${it.message}" }
        val ok = runCatching { manager.hasPermission(device) }.getOrDefault(false)
        Log.i(TAG, "${device.deviceName}: ${if (ok) "allowed through the shell" else "not allowed: ${out.lines().lastOrNull().orEmpty()}"}")
        DebugLog.note(app, "USB ${device.deviceName}: ${if (ok) "allowed through the privileged shell" else "the shell could not allow it: ${out.lines().lastOrNull().orEmpty()}"}")
        return ok
    }
}

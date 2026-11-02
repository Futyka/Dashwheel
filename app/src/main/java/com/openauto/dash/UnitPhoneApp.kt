package com.openauto.dash

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * The phone button, wherever a dashboard has one (the Quick dial's Dialer,
 * the rail's and the themes' phone keys): Android's dialer when the unit has
 * one, else the unit's own Bluetooth phone app. Most head units have no
 * dialer for [Intent.ACTION_DIAL]: their phone, contacts and call log are a
 * car app of their own (`com.qf.bluetooth`, `com.syu.bt`...). Asking only for
 * the dialer left the button dead there, or opened the app drawer (issue #118,
 * a Junsun V1 Plus).
 */
internal object UnitPhoneApp {
    /** The firmwares' own Bluetooth phone apps, tried before anything that only looks like one. */
    internal val KNOWN = listOf(
        "com.qf.bluetooth", // QF (Hizpo, ROCO)
        "com.syu.bt", // FYT / SYU (Joying, Junsun, Atoto, Teyes...)
        "com.microntek.bluetooth", // MTC (PX5, PX6)
        "com.autochips.bluetooth" // AC8227 / AC8257 units
    )

    /** Words in a package name that make it a phone app, best first. */
    private val PACKAGE_WORDS = listOf("dialer", "btphone", "phone", "bluetooth", "bt")

    /** Words in an app's name that make it a phone app, in the unit's languages. */
    private val LABEL_WORDS = listOf(
        "phone", "dialer", "bluetooth", "bt", "téléphone", "telefon", "teléfono", "telefono", "telefone", "telefoon",
        "телефон", "telefonas", "contacts"
    )

    /** A phone app's package can still be a Bluetooth music player or a settings page: never those. */
    private val NOT_PHONE = listOf("music", "audio", "a2dp", "setting", "media", "player")

    /**
     * Opens the unit's phone: Android's dialer, else the unit's phone app,
     * else [orElse] (the app drawer, say). False when nothing opened.
     */
    fun open(context: Context, orElse: (() -> Unit)? = null): Boolean {
        val dial = Intent(Intent.ACTION_DIAL)
        val hasDialer = runCatching { context.packageManager.resolveActivity(dial, PackageManager.MATCH_DEFAULT_ONLY) != null }.getOrDefault(false)
        if (hasDialer && context.launchSafely(dial)) return true
        val app = phoneApp(context)
        if (app != null && AppLauncher.launch(context, app)) return true
        orElse?.invoke()
        return false
    }

    /** The unit's own phone app, or null when it has none Dashwheel can see. */
    fun phoneApp(context: Context): String? {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = runCatching {
            pm.queryIntentActivities(launcher, 0).mapNotNull { r ->
                val info = r.activityInfo ?: return@mapNotNull null
                info.packageName to (runCatching { r.loadLabel(pm).toString() }.getOrNull() ?: "")
            }
        }.getOrDefault(emptyList())
        return pickPhoneApp(apps, context.packageName)
    }

    /**
     * Of the launchable [apps] (package to name), the unit's phone app: a
     * known firmware one first, then the one whose package or name says
     * "phone" or "bluetooth" most clearly; never [own], never a music player.
     */
    internal fun pickPhoneApp(apps: List<Pair<String, String>>, own: String): String? {
        val candidates = apps.filter { it.first != own }.distinctBy { it.first }
        KNOWN.firstOrNull { k -> candidates.any { it.first == k } }?.let { return it }
        return candidates
            .mapNotNull { (pkg, label) -> score(pkg, label)?.let { pkg to it } }
            .minByOrNull { it.second }
            ?.first
    }

    /** Lower is better; null: not a phone app. */
    private fun score(pkg: String, label: String): Int? {
        val p = pkg.lowercase()
        val l = label.lowercase()
        if (NOT_PHONE.any { it in p || it in l }) return null
        val parts = p.split('.', '_')
        val byPackage = PACKAGE_WORDS.indexOfFirst { w -> parts.any { it == w || (w.length > 2 && w in it) } }
        val labelWords = l.split(' ', '-', '_', '.', '/')
        val byLabel = LABEL_WORDS.indexOfFirst { w -> labelWords.any { it == w } || (w.length > 2 && w in l) }
        return when {
            byPackage >= 0 && byLabel >= 0 -> byPackage
            byPackage >= 0 -> PACKAGE_WORDS.size + byPackage
            byLabel >= 0 -> 2 * PACKAGE_WORDS.size + byLabel
            else -> null
        }
    }
}

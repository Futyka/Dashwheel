package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnitPhoneAppTest {
    private val own = "com.openauto.dash"

    @Test
    fun theFirmwaresOwnPhoneApp_comesFirst() {
        val apps = listOf("com.example.dialer" to "Dialer", "com.syu.bt" to "Bluetooth", own to "Dashwheel")
        assertEquals("com.syu.bt", UnitPhoneApp.pickPhoneApp(apps, own))
    }

    @Test
    fun anUnknownUnit_getsTheAppWhosePackageSaysPhone() {
        val apps = listOf("com.spotify.music" to "Spotify", "com.junsun.btphone" to "BT", "com.android.settings" to "Settings")
        assertEquals("com.junsun.btphone", UnitPhoneApp.pickPhoneApp(apps, own))
    }

    @Test
    fun theAppsNameIsEnough_inTheUnitsLanguage() {
        val apps = listOf("com.example.carui" to "Telefon", "com.example.radio" to "Radio")
        assertEquals("com.example.carui", UnitPhoneApp.pickPhoneApp(apps, own))
    }

    @Test
    fun aBluetoothMusicPlayer_isNotThePhone() {
        val apps = listOf("com.example.btmusic" to "BT Music", "com.example.bluetooth.audio" to "Bluetooth audio")
        assertNull(UnitPhoneApp.pickPhoneApp(apps, own))
    }

    @Test
    fun nothingLikeAPhone_isNull() {
        assertNull(UnitPhoneApp.pickPhoneApp(listOf("com.waze" to "Waze", "com.debt.app" to "Budget"), own))
    }
}

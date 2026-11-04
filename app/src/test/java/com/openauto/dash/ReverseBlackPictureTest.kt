package com.openauto.dash

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A camera with no signal behind it gives a black picture: Dashwheel gives its own up. */
class ReverseBlackPictureTest {

    @Test
    fun blackAndNearBlack_isNoSignal() {
        assertTrue(isBlackPicture(IntArray(64 * 36) { 0xFF000000.toInt() }))
        assertTrue(isBlackPicture(IntArray(64 * 36) { 0xFF0A0C08.toInt() }))
    }

    @Test
    fun aDarkSceneWithAnythingInIt_isAPicture() {
        val night = IntArray(64 * 36) { 0xFF050505.toInt() }
        // The reversing lights on the ground, or the camera's date stamp: one pixel is enough.
        night[700] = 0xFF303030.toInt()
        assertFalse(isBlackPicture(night))
        assertFalse(isBlackPicture(IntArray(64 * 36) { 0xFF404850.toInt() }))
    }
}

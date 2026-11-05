package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Test

class UsbGrantTest {

    @Test
    fun theDeviceIsAllowedByPathAndUidThroughTheShell() {
        assertEquals(
            "CLASSPATH=/data/app/x/base.apk app_process /system/bin com.openauto.dash.UsbGrant '/dev/bus/usb/001/004' 10123",
            UsbGrant.command("/data/app/x/base.apk", "/dev/bus/usb/001/004", 10123)
        )
    }
}

package com.winlator.inputcontrols

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class DualSenseSupportTest {
    @Test
    fun `maps hid-generic Sony button order to standard gamepad keys`() {
        mapOf(
            0x130 to KeyEvent.KEYCODE_BUTTON_X,
            0x131 to KeyEvent.KEYCODE_BUTTON_A,
            0x132 to KeyEvent.KEYCODE_BUTTON_B,
            0x133 to KeyEvent.KEYCODE_BUTTON_Y,
            0x134 to KeyEvent.KEYCODE_BUTTON_L1,
            0x135 to KeyEvent.KEYCODE_BUTTON_R1,
            0x136 to KeyEvent.KEYCODE_BUTTON_L2,
            0x137 to KeyEvent.KEYCODE_BUTTON_R2,
            0x138 to KeyEvent.KEYCODE_BUTTON_SELECT,
            0x139 to KeyEvent.KEYCODE_BUTTON_START,
            0x13a to KeyEvent.KEYCODE_BUTTON_THUMBL,
            0x13b to KeyEvent.KEYCODE_BUTTON_THUMBR,
            0x13c to KeyEvent.KEYCODE_BUTTON_MODE,
            0x13d to KeyEvent.KEYCODE_BUTTON_1,
        ).forEach { (scanCode, expected) ->
            assertEquals(expected, DualSenseSupport.remapGenericScanCode(scanCode, KeyEvent.KEYCODE_UNKNOWN))
        }
        assertEquals(
            KeyEvent.KEYCODE_DPAD_UP,
            DualSenseSupport.remapGenericScanCode(103, KeyEvent.KEYCODE_DPAD_UP),
        )
    }

    @Test
    fun `normalizes centered trigger axes to the 0 to 1 range`() {
        assertEquals(0f, DualSenseSupport.normalizeTrigger(-1f, -1f, 1f), 0.0001f)
        assertEquals(0.5f, DualSenseSupport.normalizeTrigger(0f, -1f, 1f), 0.0001f)
        assertEquals(1f, DualSenseSupport.normalizeTrigger(1f, -1f, 1f), 0.0001f)
        assertEquals(0.25f, DualSenseSupport.normalizeTrigger(0.25f, 0f, 1f), 0.0001f)
        assertEquals(1f, DualSenseSupport.normalizeTrigger(1.02f, 0f, 1f), 0.0001f)
        assertEquals(0f, DualSenseSupport.normalizeTrigger(0.5f, 0f, 0f), 0.0001f)
    }
}

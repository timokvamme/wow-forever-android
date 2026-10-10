package com.winlator.inputcontrols;

import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compatibility helpers for Sony PlayStation controllers (DualSense, DualSense Edge, DualShock 4).
 *
 * With the kernel's hid-playstation driver, Android's Vendor_054c key layouts give standard
 * gamepad key codes and axes. Phones whose kernel lacks that driver fall back to hid-generic and
 * Generic.kl, which reports the raw HID button order instead: Square arrives as BUTTON_A, Cross as
 * BUTTON_B, Circle as BUTTON_C, and the analog triggers arrive on AXIS_RX/AXIS_RY. That layout is
 * recognised by the device claiming BUTTON_C, which no standard Sony layout maps.
 */
public final class DualSenseSupport {
    public static final int SONY_VENDOR_ID = 0x054c;

    // Device ids are never reused while the system runs, so caching by id is safe.
    private static final Map<Integer, Boolean> genericLayoutCache = new ConcurrentHashMap<>();

    private DualSenseSupport() {}

    public static boolean isSony(InputDevice device) {
        return device != null && device.getVendorId() == SONY_VENDOR_ID;
    }

    /** True when a Sony pad is running on hid-generic with Generic.kl (raw HID button order). */
    public static boolean usesGenericLayout(InputDevice device) {
        if (!isSony(device)) return false;
        Boolean cached = genericLayoutCache.get(device.getId());
        if (cached != null) return cached;
        boolean[] keys = device.hasKeys(KeyEvent.KEYCODE_BUTTON_C);
        boolean generic = keys.length > 0 && keys[0];
        genericLayoutCache.put(device.getId(), generic);
        return generic;
    }

    public static int remapKeyCode(InputDevice device, KeyEvent event, int fallbackKeyCode) {
        if (event == null || !usesGenericLayout(device)) return fallbackKeyCode;
        return remapGenericScanCode(event.getScanCode(), fallbackKeyCode);
    }

    /** Maps hid-generic's BTN_GAMEPAD + n scan codes in Sony's HID button order. */
    static int remapGenericScanCode(int scanCode, int fallbackKeyCode) {
        switch (scanCode) {
            case 0x130: return KeyEvent.KEYCODE_BUTTON_X;      // Square
            case 0x131: return KeyEvent.KEYCODE_BUTTON_A;      // Cross
            case 0x132: return KeyEvent.KEYCODE_BUTTON_B;      // Circle
            case 0x133: return KeyEvent.KEYCODE_BUTTON_Y;      // Triangle
            case 0x134: return KeyEvent.KEYCODE_BUTTON_L1;
            case 0x135: return KeyEvent.KEYCODE_BUTTON_R1;
            case 0x136: return KeyEvent.KEYCODE_BUTTON_L2;
            case 0x137: return KeyEvent.KEYCODE_BUTTON_R2;
            case 0x138: return KeyEvent.KEYCODE_BUTTON_SELECT; // Create / Share
            case 0x139: return KeyEvent.KEYCODE_BUTTON_START;  // Options
            case 0x13a: return KeyEvent.KEYCODE_BUTTON_THUMBL;
            case 0x13b: return KeyEvent.KEYCODE_BUTTON_THUMBR;
            case 0x13c: return KeyEvent.KEYCODE_BUTTON_MODE;   // PS
            case 0x13d: return KeyEvent.KEYCODE_BUTTON_1;      // Touchpad click
            default: return fallbackKeyCode;
        }
    }

    /**
     * Reads a trigger reported on a centered axis (AXIS_RX/AXIS_RY on Generic.kl), which rests at
     * -1, and returns it in the 0..1 range used for AXIS_LTRIGGER/AXIS_RTRIGGER.
     */
    public static float triggerValue(MotionEvent event, int axis) {
        InputDevice device = event.getDevice();
        if (device == null) return 0f;
        InputDevice.MotionRange range = device.getMotionRange(axis, event.getSource());
        if (range == null) return 0f;
        return normalizeTrigger(event.getAxisValue(axis), range.getMin(), range.getMax());
    }

    static float normalizeTrigger(float value, float min, float max) {
        if (max <= min) return 0f;
        float normalized = min < 0f ? (value - min) / (max - min) : value;
        return Math.min(1f, Math.max(0f, normalized));
    }
}

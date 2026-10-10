package app.gamenative.utils

import android.hardware.lights.LightState
import android.hardware.lights.LightsRequest
import android.os.Build
import android.view.InputDevice
import com.winlator.inputcontrols.ExternalController
import timber.log.Timber

/**
 * Turns off a controller's lights (DualSense light bar and player LEDs) through Android's
 * InputDevice lights API, available from Android 12. The lights stay off while the session is
 * open and go back to the system default when it is closed. Needs the kernel to expose the LEDs,
 * which hid-playstation does; devices without controllable lights are left alone.
 */
object ControllerLights {
    private val sessions = mutableMapOf<Int, AutoCloseable>()

    fun turnOff(device: InputDevice?) {
        if (device == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (sessions.containsKey(device.id) || !ExternalController.isGameController(device)) return
        runCatching {
            val manager = device.lightsManager
            val lights = manager.lights
            if (lights.isEmpty()) return
            val off = LightState.Builder().setColor(0).build()
            val request = LightsRequest.Builder().apply { lights.forEach { addLight(it, off) } }.build()
            val session = manager.openSession()
            session.requestLights(request)
            sessions[device.id] = session
            Timber.i("ControllerLights: turned off %d light(s) on %s", lights.size, device.name)
        }.onFailure { Timber.w(it, "ControllerLights: could not turn off lights on %s", device.name) }
    }

    fun release(deviceId: Int) {
        sessions.remove(deviceId)?.let { runCatching { it.close() } }
    }

    fun releaseAll() {
        sessions.values.forEach { runCatching { it.close() } }
        sessions.clear()
    }
}

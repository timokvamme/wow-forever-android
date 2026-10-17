package app.gamenative

import android.content.Context
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records controller input and window focus changes to cache/input-debug.log, so a player can
 * share what happened when buttons stop reaching the game (split screen, overlays).
 */
object InputDiagnostics {
    private const val FILE_NAME = "input-debug.log"
    private const val MAX_BYTES = 512 * 1024L

    private var file: File? = null
    private val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val lastMotionLog = HashMap<Int, Long>()

    fun init(context: Context) {
        file = File(context.cacheDir, FILE_NAME)
    }

    fun logFile(context: Context): File = File(context.cacheDir, FILE_NAME)

    @Synchronized
    fun log(message: String) {
        val f = file ?: return
        runCatching {
            if (f.length() > MAX_BYTES) {
                val tail = f.readText().takeLast((MAX_BYTES / 2).toInt())
                f.writeText(tail)
            }
            f.appendText("${time.format(Date())} $message\n")
        }
    }

    private fun isController(device: InputDevice?): Boolean {
        val sources = device?.sources ?: return false
        return sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
    }

    fun key(event: KeyEvent, handled: Boolean) {
        if (!isController(event.device) || event.repeatCount > 0) return
        val action = if (event.action == KeyEvent.ACTION_DOWN) "down" else if (event.action == KeyEvent.ACTION_UP) "up" else "${event.action}"
        log(
            "key ${KeyEvent.keyCodeToString(event.keyCode)} $action scan=${event.scanCode} " +
                "dev=${event.deviceId}:${event.device?.name} handled=$handled",
        )
    }

    /** At most one line per device per second, since sticks send a stream of events. */
    fun motion(event: MotionEvent?, handled: Boolean) {
        val e = event ?: return
        if (!isController(e.device)) return
        val now = System.currentTimeMillis()
        if (now - (lastMotionLog[e.deviceId] ?: 0L) < 1000) return
        lastMotionLog[e.deviceId] = now
        log("motion dev=${e.deviceId}:${e.device?.name} handled=$handled")
    }
}

package app.gamenative.ui.screen.wow

import app.gamenative.InputDiagnostics
import com.winlator.xserver.XKeycode
import com.winlator.xserver.XServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Presses Enter once, a set time after the game window opens, to confirm the preselected game
 * account at login (the same as pressing the gamepad's confirm button). The launcher arms it for
 * each launch; the first game window that maps fires it.
 */
object LoginAutoConfirm {
    @Volatile
    private var pendingDelaySeconds = 0

    fun arm(delaySeconds: Int) {
        pendingDelaySeconds = delaySeconds.coerceAtLeast(0)
    }

    fun onGameWindowMapped(xServer: XServer) {
        val seconds = pendingDelaySeconds
        if (seconds <= 0) return
        pendingDelaySeconds = 0
        CoroutineScope(Dispatchers.Default).launch {
            delay(seconds * 1000L)
            Timber.i("Pressing Enter to confirm the game account, %ds after the game window opened", seconds)
            InputDiagnostics.log("auto-confirm: pressing Enter after ${seconds}s")
            runCatching {
                xServer.injectKeyPress(XKeycode.KEY_ENTER, 0)
                delay(80)
                xServer.injectKeyRelease(XKeycode.KEY_ENTER)
            }.onFailure { Timber.w(it, "Auto-confirm key press failed") }
        }
    }
}

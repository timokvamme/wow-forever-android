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
 * Presses Enter at set times after the game window opens, to get through login prompts such as
 * confirming the preselected game account (the same as the gamepad's confirm button). Each delay
 * counts from the press before it, the first from the window opening. The launcher arms it for each
 * launch; the first game window that maps fires it.
 */
object LoginAutoConfirm {
    @Volatile
    private var pending: List<Int> = emptyList()

    fun arm(delaysSeconds: List<Int>) {
        pending = delaysSeconds.filter { it > 0 }
    }

    fun onGameWindowMapped(xServer: XServer) {
        val delays = pending
        if (delays.isEmpty()) return
        pending = emptyList()
        CoroutineScope(Dispatchers.Default).launch {
            delays.forEachIndexed { i, seconds ->
                delay(seconds * 1000L)
                Timber.i("Auto-confirm press %d of %d after %ds", i + 1, delays.size, seconds)
                InputDiagnostics.log("auto-confirm: press ${i + 1} of ${delays.size} after ${seconds}s")
                runCatching {
                    xServer.injectKeyPress(XKeycode.KEY_ENTER, 0)
                    delay(80)
                    xServer.injectKeyRelease(XKeycode.KEY_ENTER)
                }.onFailure { Timber.w(it, "Auto-confirm key press failed") }
            }
        }
    }
}

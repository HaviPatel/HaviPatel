package com.reatv.launcher.ui

import android.os.SystemClock
import android.view.KeyEvent
import android.view.SoundEffectConstants
import android.view.View
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * tvOS-style interface sounds: a tick when the D-pad moves focus, a click on select. They're the
 * system's own sound effects, so Fire TV's Navigation Sounds setting turns them off with the rest.
 */
class UiSounds(private val view: View) {
    private var direction = NONE
    private var pressedAt = 0L

    fun onDpad(keyCode: Int) {
        direction = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> SoundEffectConstants.NAVIGATION_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> SoundEffectConstants.NAVIGATION_RIGHT
            KeyEvent.KEYCODE_DPAD_UP -> SoundEffectConstants.NAVIGATION_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> SoundEffectConstants.NAVIGATION_DOWN
            else -> return
        }
        pressedAt = SystemClock.uptimeMillis()
    }

    /**
     * Focus landed on something. Ticks only for the press that moved it there, once: focus the app
     * moves itself (opening an overlay, coming back Home) stays silent, as does pressing into a wall.
     */
    fun onFocusMoved() {
        val d = direction
        if (d == NONE || SystemClock.uptimeMillis() - pressedAt > PRESS_WINDOW_MS) return
        direction = NONE
        view.playSoundEffect(d)
    }

    fun onSelect() = view.playSoundEffect(SoundEffectConstants.CLICK)

    private companion object {
        const val NONE = -1
        const val PRESS_WINDOW_MS = 300L
    }
}

val LocalUiSounds = staticCompositionLocalOf<UiSounds?> { null }

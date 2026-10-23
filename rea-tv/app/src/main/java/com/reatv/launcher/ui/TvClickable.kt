package com.reatv.launcher.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.node.ModifierNodeElement

/**
 * Remote-friendly click: OK fires on release, holding OK fires [onLongClick] (Android marks the
 * first key-repeat as a long press). A release without a matching press is ignored, so the OK
 * release that follows a long press never "clicks" whatever just opened under it.
 *
 * Place it before `focusable()` so it sits on the focused node's key path.
 */
fun Modifier.tvClickable(onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier =
    this then TvClickElement(onClick, onLongClick)

private class TvClickElement(
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)?,
) : ModifierNodeElement<TvClickNode>() {
    override fun create() = TvClickNode(onClick, onLongClick)

    override fun update(node: TvClickNode) {
        node.onClick = onClick
        node.onLongClick = onLongClick
    }

    override fun equals(other: Any?): Boolean =
        other is TvClickElement && other.onClick === onClick && other.onLongClick === onLongClick

    override fun hashCode(): Int = 31 * onClick.hashCode() + (onLongClick?.hashCode() ?: 0)
}

private class TvClickNode(
    var onClick: () -> Unit,
    var onLongClick: (() -> Unit)?,
) : Modifier.Node(), KeyInputModifierNode {
    private var armed = false
    private var longFired = false

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val native = event.nativeKeyEvent
        if (!isSelectKey(native.keyCode)) return false
        when (native.action) {
            AndroidKeyEvent.ACTION_DOWN -> {
                if (native.repeatCount == 0) {
                    armed = true
                    longFired = false
                } else {
                    val long = onLongClick
                    if (armed && !longFired && long != null) {
                        longFired = true
                        long()
                    }
                }
                return true
            }
            AndroidKeyEvent.ACTION_UP -> {
                val click = armed && !longFired
                armed = false
                longFired = false
                if (click) onClick()
                return true
            }
        }
        return false
    }

    override fun onPreKeyEvent(event: KeyEvent): Boolean = false

    override fun onDetach() {
        armed = false
        longFired = false
    }
}

fun isSelectKey(keyCode: Int): Boolean = keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
    keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
    keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER ||
    keyCode == AndroidKeyEvent.KEYCODE_BUTTON_A

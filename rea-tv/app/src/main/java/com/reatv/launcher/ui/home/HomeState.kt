package com.reatv.launcher.ui.home

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import com.reatv.launcher.data.AppEntry

/** Home layout, derived from screen size so it looks the same at 720p and 1080p (≈960x540 dp). */
@Immutable
class HomeMetrics(val width: Dp, val height: Dp, val columns: Int) {
    val edge = width * 0.05f
    val gap = width * 0.026f
    val tileW = (width - edge * 2 - gap * (columns - 1)) / columns
    val tileH = tileW * 0.5625f
    val corner = tileW * 0.05f
    val labelSpace = 30.dp
    val pitch = tileH + labelSpace + 14.dp
    val firstRowTop = height * 0.6f
    private val pivot = height * 0.13f

    /** tvOS-like scrolling: the top row shows the Top Shelf, deeper rows pin near the top. */
    fun scrollFor(row: Int, rows: Int): Dp {
        if (row <= 0) return 0.dp
        val target = firstRowTop + pitch * row - pivot
        val maxScroll = max(0.dp, firstRowTop + pitch * rows + 40.dp - height)
        return max(0.dp, min(target, maxScroll))
    }

    fun tileOffset(row: Int, column: Int, scroll: Dp): DpOffset =
        DpOffset(edge + (tileW + gap) * column, firstRowTop + pitch * row - scroll)
}

@Stable
class HomeState {
    var focusedKey: String? by mutableStateOf(null)
    var focusedRow by mutableIntStateOf(0)
    var focusedColumn by mutableIntStateOf(0)

    /** Bumped on every tile focus, so effects re-run even when focus returns to the same tile. */
    var focusTick by mutableIntStateOf(0)

    /** App focused in the top row: drives the Top Shelf and the dynamic wallpaper. */
    var topApp: AppEntry? by mutableStateOf(null)

    /** Focus is inside the Top Shelf's titles (Up from the top row), browsing them like tvOS. */
    var shelfFocused by mutableStateOf(false)

    /** False while another app is in front: the shelf stops advancing and loading. */
    var visible by mutableStateOf(true)

    /** App being rearranged ("jiggle" mode), or null. */
    var movingKey: String? by mutableStateOf(null)

    /** Set when REA TV launches something, so the following Home intent restores focus instead of resetting. */
    var launchedApp = false

    /** Last D-pad direction (-1, 0, 1), used for the arrival tilt of the newly focused tile. */
    var lastDirX = 0
    var lastDirY = 0

    internal val requesters = HashMap<String, FocusRequester>()

    fun onTileFocused(app: AppEntry, row: Int, column: Int) {
        focusedKey = app.key
        focusedRow = row
        focusedColumn = column
        focusTick++
        if (row == 0) topApp = app
    }

    fun requestFocus(key: String?): Boolean {
        val requester = key?.let { requesters[it] } ?: return false
        return try {
            requester.requestFocus()
            true
        } catch (e: IllegalStateException) {
            false
        }
    }
}

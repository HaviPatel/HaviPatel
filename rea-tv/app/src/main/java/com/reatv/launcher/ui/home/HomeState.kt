package com.reatv.launcher.ui.home

import android.graphics.Bitmap
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

    /** The Liquid Glass dock around the top row, as on tvOS 26: its margin and its corner. */
    val dockPad = 16.dp
    val dockCorner = corner + dockPad

    /** Room the dock adds under the top row, for its bottom edge and the label beneath it. */
    val dockGap = dockPad + 4.dp

    /** The Top Shelf's art runs down behind the dock, then fades into the black behind the rows. */
    val shelfHeight = firstRowTop + tileH + dockPad + 28.dp

    fun rowTop(row: Int): Dp = firstRowTop + pitch * row + if (row > 0) dockGap else 0.dp

    /** tvOS-like scrolling: the top row shows the Top Shelf, deeper rows pin near the top. */
    fun scrollFor(row: Int, rows: Int): Dp {
        if (row <= 0) return 0.dp
        val target = rowTop(row) - pivot
        val maxScroll = max(0.dp, rowTop(rows) + 40.dp - height)
        return max(0.dp, min(target, maxScroll))
    }

    fun tileOffset(row: Int, column: Int, scroll: Dp): DpOffset =
        DpOffset(edge + (tileW + gap) * column, rowTop(row) - scroll)
}

/** The Top Shelf's current art ([image], or a wash of [color] when null), as the dock sees it. */
@Immutable
class ShelfGlass(val image: Bitmap?, val color: Int)

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

    /** What the Top Shelf shows right now, for the dock's glass to refract. */
    var shelfArt: ShelfGlass? by mutableStateOf(null)

    /** tvOS "Edit Home Screen": every app jiggles; OK picks one up ([movingKey]) and drops it. */
    var editing by mutableStateOf(false)

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

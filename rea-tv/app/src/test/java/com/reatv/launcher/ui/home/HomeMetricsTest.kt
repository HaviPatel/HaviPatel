package com.reatv.launcher.ui.home

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeMetricsTest {
    private val metrics = HomeMetrics(width = 960.dp, height = 540.dp, columns = 5)

    @Test
    fun fiveTilesAndGapsFillTheSafeArea() {
        val used = metrics.tileW * 5 + metrics.gap * 4 + metrics.edge * 2
        assertEquals(960f, used.value, 0.01f)
        assertEquals(metrics.tileW.value * 9f / 16f, metrics.tileH.value, 0.01f)
    }

    @Test
    fun topRowNeverScrolls() {
        assertEquals(0f, metrics.scrollFor(row = 0, rows = 10).value, 0f)
    }

    @Test
    fun deeperRowsPinNearTheTop() {
        val scroll = metrics.scrollFor(row = 3, rows = 10)
        val rowTopOnScreen = metrics.tileOffset(row = 3, column = 0, scroll = scroll).y
        assertEquals(540f * 0.13f, rowTopOnScreen.value, 0.01f)
    }

    @Test
    fun onlyTheDockAddsRoomBelowItsRow() {
        assertEquals((metrics.pitch + metrics.dockGap).value, (metrics.rowTop(1) - metrics.rowTop(0)).value, 0.01f)
        assertEquals(metrics.pitch.value, (metrics.rowTop(3) - metrics.rowTop(2)).value, 0.01f)
        // The second row starts below the dock and below the shelf's art.
        assertTrue(metrics.rowTop(1) > metrics.rowTop(0) + metrics.tileH + metrics.dockPad)
        assertTrue(metrics.rowTop(1) >= metrics.shelfHeight)
    }

    @Test
    fun scrollStopsAtTheEndOfContent() {
        val rows = 3
        val last = metrics.scrollFor(row = rows - 1, rows = rows)
        val contentBottom = metrics.firstRowTop + metrics.pitch * rows + metrics.dockGap + 40.dp
        assertEquals((contentBottom - 540.dp).value, last.value, 0.01f)
        assertTrue(metrics.scrollFor(row = 1, rows = rows) <= last)
    }
}

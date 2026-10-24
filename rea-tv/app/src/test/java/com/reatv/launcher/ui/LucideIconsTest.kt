package com.reatv.launcher.ui

import androidx.compose.ui.graphics.vector.addPathNodes
import org.junit.Assert.assertTrue
import org.junit.Test

class LucideIconsTest {
    /** A malformed path would only fail at runtime on the TV; parse every one here instead. */
    @Test
    fun everyIconPathParses() {
        for (icon in ReaIcon.entries) {
            val paths = lucidePaths(icon)
            assertTrue("$icon has no paths", paths.isNotEmpty())
            for (data in paths) {
                val nodes = addPathNodes(data)
                assertTrue("$icon: '$data' parsed to nothing", nodes.isNotEmpty())
            }
        }
    }
}

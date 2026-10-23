package com.reatv.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppOrderTest {
    private fun app(pkg: String, label: String = pkg, system: Boolean = false) =
        AppEntry(key = pkg, label = label, packageName = pkg, component = null, isSystem = system, category = -1)

    private val settingsTile = AppEntry(
        key = AppRepository.SETTINGS_KEY,
        label = "Settings",
        packageName = "",
        component = null,
        isSystem = true,
        category = -1,
        kind = AppEntry.Kind.SystemSettings,
    )

    @Test
    fun firstRunPutsStreamingAppsFirstAndSettingsLast() {
        val all = listOf(settingsTile, app("org.example.zeta", "Zeta"), app("com.netflix.ninja", "Netflix"), app("org.example.alpha", "Alpha"))
        val keys = AppOrder.arrange(all, order = emptyList(), hidden = emptyList()).map { it.key }
        assertEquals(listOf("com.netflix.ninja", "org.example.alpha", "org.example.zeta", AppRepository.SETTINGS_KEY), keys)
    }

    @Test
    fun savedOrderWinsAndNewAppsAreAppendedAlphabetically() {
        val all = listOf(app("a", "A"), app("b", "B"), app("c", "C"), app("new2", "Yak"), app("new1", "Xylo"))
        val keys = AppOrder.arrange(all, order = listOf("c", "a", "gone", "b"), hidden = emptyList()).map { it.key }
        assertEquals(listOf("c", "a", "b", "new1", "new2"), keys)
    }

    @Test
    fun hiddenAppsAreRemoved() {
        val all = listOf(app("a"), app("b"), app("c"))
        val keys = AppOrder.arrange(all, order = listOf("a", "b", "c"), hidden = listOf("b")).map { it.key }
        assertEquals(listOf("a", "c"), keys)
    }

    @Test
    fun moveReordersAndClampsTarget() {
        val visible = listOf(app("a"), app("b"), app("c"), app("d"))
        assertEquals(listOf("b", "c", "a", "d"), AppOrder.move(visible, "a", 2))
        assertEquals(listOf("b", "c", "d", "a"), AppOrder.move(visible, "a", 99))
        assertEquals(listOf("d", "a", "b", "c"), AppOrder.move(visible, "d", -5))
        assertEquals(listOf("a", "b", "c", "d"), AppOrder.move(visible, "missing", 1))
    }

    @Test
    fun alphabeticalKeepsSettingsLast() {
        val all = listOf(settingsTile, app("z", "zulu"), app("a", "Alpha"))
        assertEquals(listOf("a", "z", AppRepository.SETTINGS_KEY), AppOrder.alphabetical(all))
    }
}

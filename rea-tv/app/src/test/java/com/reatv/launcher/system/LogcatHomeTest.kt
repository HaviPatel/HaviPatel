package com.reatv.launcher.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogcatHomeTest {
    @Test
    fun homePressFromTheSystem() {
        val line = "I/ActivityManager(  612): START u0 {act=android.intent.action.MAIN cat=[android.intent.category.HOME] " +
            "flg=0x10200000 cmp=com.amazon.tv.launcher/.ui.HomeActivity_vNext} from uid 1000 on display 0"
        val start = LogcatHome.parseHomeStart(line)!!
        assertEquals("com.amazon.tv.launcher", start.activity.packageName)
        assertEquals("com.amazon.tv.launcher.ui.HomeActivity_vNext", start.activity.className)
        assertEquals(1000, start.fromUid)
    }

    @Test
    fun ourOwnHomeStartCarriesOurUid() {
        val line = "I/ActivityManager(  612): START u0 {act=android.intent.action.MAIN cat=[android.intent.category.HOME] " +
            "flg=0x10000000 cmp=com.reatv.launcher/.MainActivity} from uid 10071 on display 0"
        val start = LogcatHome.parseHomeStart(line)!!
        assertEquals("com.reatv.launcher", start.activity.packageName)
        assertEquals(10071, start.fromUid)
    }

    @Test
    fun ordinaryAppStartsAreIgnored() {
        val line = "I/ActivityManager(  612): START u0 {act=android.intent.action.MAIN cat=[android.intent.category.LEANBACK_LAUNCHER] " +
            "flg=0x10200000 cmp=com.netflix.ninja/.MainActivity} from uid 10071 on display 0"
        assertNull(LogcatHome.parseHomeStart(line))
        assertNull(LogcatHome.parseHomeStart("I/ActivityManager(  612): Start proc 4321:com.netflix.ninja/u0a70"))
    }

    @Test
    fun aPageOpenedInsideAmazonsHomeIsNotAHomePress() {
        // e.g. the remote's Prime Video button or a voice result landing on a page of Amazon's home.
        val line = "I/ActivityManager(  612): START u0 {act=android.intent.action.VIEW dat=amzn://apps/android?p=prime " +
            "flg=0x10000000 cmp=com.amazon.tv.launcher/.ui.HomeActivity_vNext (has extras)} from uid 10032 on display 0"
        val start = LogcatHome.parseStart(line)!!
        assertEquals("com.amazon.tv.launcher.ui.HomeActivity_vNext", start.activity.className)
        assertFalse(start.home)
        assertEquals(10032, start.fromUid)
        assertNull(LogcatHome.parseHomeStart(line))
    }

    @Test
    fun homePressIsAHomeStart() {
        val line = "I/ActivityManager(  612): START u0 {act=android.intent.action.MAIN cat=[android.intent.category.HOME] " +
            "flg=0x10200000 cmp=com.amazon.tv.launcher/.ui.HomeActivity_vNext} from uid 1000 on display 0"
        assertTrue(LogcatHome.parseStart(line)!!.home)
    }

    @Test
    fun resumeLineGivesTheActivity() {
        val resumed = LogcatHome.parseResume("I/am_resume_activity(  612): [0,183836245,8,com.amazon.tv.launcher/.ui.HomeActivity_vNext]")!!
        assertEquals("com.amazon.tv.launcher", resumed.packageName)
        assertEquals("com.amazon.tv.launcher.ui.HomeActivity_vNext", resumed.className)

        val full = LogcatHome.parseResume("I/am_resume_activity( 612): [0,1,2,com.netflix.ninja/com.netflix.ninja.MainActivity]")!!
        assertEquals("com.netflix.ninja.MainActivity", full.className)
    }

    @Test
    fun malformedLinesAreIgnored() {
        assertNull(LogcatHome.parseResume("I/am_resume_activity(  612): [0,1,2]"))
        assertNull(LogcatHome.parseResume("I/am_focused_activity(  612): [0,com.x/.Y]"))
        assertNull(LogcatHome.parseResume("--------- beginning of events"))
    }

    @Test
    fun homeScreenMatchingSkipsOtherAmazonScreens() {
        val classes = setOf("com.amazon.tv.launcher.ui.HomeActivity_vNext")
        assert(HomeRedirect.isHomeScreen("com.amazon.tv.launcher.ui.HomeActivity_vNext", classes))
        assert(!HomeRedirect.isHomeScreen("com.amazon.tv.launcher.ui.SettingsActivity", classes))
        assert(!HomeRedirect.isHomeScreen("android.app.Dialog", classes))
        assert(HomeRedirect.isHomeScreen("com.amazon.firehomestarter.HomeStarterActivity", emptySet()))
    }
}

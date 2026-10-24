package com.reatv.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LogoKeyTest {
    private val w = 320
    private val h = 180

    private fun banner(fill: (x: Int, y: Int) -> Int) = IntArray(w * h) { fill(it % w, it / w) }

    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun inRect(x: Int, y: Int) = x in 100 until 220 && y in 60 until 120

    private fun alphaAt(logo: LogoKey.Logo, x: Int, y: Int) = logo.pixels[y * logo.width + x] ushr 24

    @Test
    fun flatFillIsKeyedAndCroppedToTheLogo() {
        val logo = LogoKey.extract(banner { x, y -> if (inRect(x, y)) rgb(255, 255, 255) else rgb(20, 90, 200) }, w, h)
        assertNotNull(logo)
        logo!!
        assertEquals(124, logo.width) // 120 px of logo plus 2 px either side
        assertEquals(64, logo.height)
        assertEquals(255, alphaAt(logo, 60, 30))
        assertEquals(0xFFFFFF, logo.pixels[30 * logo.width + 60] and 0xFFFFFF)
        assertEquals(0, alphaAt(logo, 0, 0))
        assertFalse(logo.reversed)
    }

    @Test
    fun gradientFillKeysCleanly() {
        val logo = LogoKey.extract(
            banner { x, y -> if (inRect(x, y)) rgb(229, 9, 20) else rgb(30 + y / 3, 30 + y / 3, 60 + x / 8) },
            w, h,
        )
        assertNotNull(logo)
        logo!!
        assertEquals(255, alphaAt(logo, 60, 30))
        assertEquals(0, alphaAt(logo, 0, 0))
        assertEquals(0, alphaAt(logo, logo.width - 1, logo.height - 1))
    }

    @Test
    fun darkInkTurnsLightForTheDarkShelf() {
        val logo = LogoKey.extract(banner { x, y -> if (inRect(x, y)) rgb(40, 40, 40) else rgb(255, 255, 255) }, w, h)
        assertNotNull(logo)
        logo!!
        assertTrue(logo.reversed)
        val core = logo.pixels[30 * logo.width + 60]
        assertTrue("ink should be light, was ${Integer.toHexString(core)}", ((core shr 8) and 0xFF) > 200)
    }

    @Test
    fun vividInkKeepsItsColor() {
        val logo = LogoKey.extract(banner { x, y -> if (inRect(x, y)) rgb(229, 9, 20) else rgb(255, 255, 255) }, w, h)!!
        assertFalse(logo.reversed)
        assertEquals(0xE50914, logo.pixels[30 * logo.width + 60] and 0xFFFFFF)
    }

    @Test
    fun photographicBannerIsRejected() {
        val random = Random(7)
        assertNull(LogoKey.extract(banner { _, _ -> rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256)) }, w, h))
    }

    @Test
    fun logoRunningIntoTheBorderIsRejected() {
        val logo = LogoKey.extract(banner { x, _ -> if (x in 100 until 220) rgb(255, 255, 255) else rgb(20, 20, 24) }, w, h)
        assertNull(logo)
    }

    @Test
    fun bannerThatIsAllLogoIsRejected() {
        val logo = LogoKey.extract(
            banner { x, y -> if (x in 4 until w - 4 && y in 4 until h - 4) rgb(250, 200, 0) else rgb(10, 10, 10) },
            w, h,
        )
        assertNull(logo)
    }

    @Test
    fun transparentBannerKeepsItsOwnAlpha() {
        val logo = LogoKey.extract(banner { x, y -> if (inRect(x, y)) rgb(0, 200, 120) else 0 }, w, h)
        assertNotNull(logo)
        logo!!
        assertEquals(255, alphaAt(logo, 60, 30))
        assertEquals(0, alphaAt(logo, 0, 0))
    }
}

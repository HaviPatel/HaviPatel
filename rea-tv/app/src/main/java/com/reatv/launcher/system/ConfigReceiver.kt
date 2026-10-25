package com.reatv.launcher.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.reatv.launcher.rea

/**
 * Takes a TMDB key from a computer, since typing one with a remote is no fun:
 * `adb shell am broadcast -n com.reatv.launcher/.system.ConfigReceiver -a com.reatv.launcher.SET_TMDB_KEY --es key KEY`
 * (scripts/rea.sh tmdb KEY). The manifest requires DUMP of the sender, which only the ADB shell holds.
 */
class ConfigReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SET_TMDB_KEY) return
        // "none" clears it: the device shell can't pass an empty argument through.
        val key = intent.getStringExtra("key").orEmpty().filter { it.isLetterOrDigit() || it in "._-" }.takeUnless { it == "none" }.orEmpty()
        val rea = context.rea
        rea.settings.tmdbKey = key
        rea.shelf.resetCatalogs()
        resultCode = 1
        resultData = if (key.isEmpty()) "TMDB key removed" else "TMDB key saved"
    }

    companion object {
        const val ACTION_SET_TMDB_KEY = "com.reatv.launcher.SET_TMDB_KEY"
    }
}

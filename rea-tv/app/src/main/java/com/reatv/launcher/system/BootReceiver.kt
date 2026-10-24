package com.reatv.launcher.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.reatv.launcher.rea

/** After a reboot, start the Fire OS 6 Home watcher and bring REA TV up over Amazon's home. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!context.rea.settings.replaceAmazonHome || !SystemInfo.isFireTv(context)) return
        HomeWatchService.start(context, fromBoot = true)
    }
}

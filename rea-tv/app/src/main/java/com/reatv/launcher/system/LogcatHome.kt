package com.reatv.launcher.system

/**
 * Parses the two system-log lines the Fire OS 6 Home watcher reacts to (logcat `-v brief`):
 *
 *     I/ActivityManager(  612): START u0 {act=android.intent.action.MAIN cat=[android.intent.category.HOME]
 *         flg=0x10200000 cmp=com.amazon.tv.launcher/.ui.HomeActivity_vNext} from uid 1000 on display 0
 *     I/am_resume_activity(  612): [0,183836245,8,com.amazon.tv.launcher/.ui.HomeActivity_vNext]
 *
 * The first is a Home press (or anything else starting the HOME intent), the second any activity
 * coming back to the front, e.g. Back out of an app onto Amazon's home. Plain Kotlin, unit tested.
 */
object LogcatHome {
    data class Activity(val packageName: String, val className: String)

    data class HomeStart(val activity: Activity, val fromUid: Int?)

    private val CMP = Regex("""cmp=([\w.]+)/([\w.$]+)""")
    private val UID = Regex("""from uid (\d+)""")

    fun parseHomeStart(line: String): HomeStart? {
        if (!line.contains("ActivityManager") || !line.contains("START ") ||
            !line.contains("android.intent.category.HOME")
        ) {
            return null
        }
        val match = CMP.find(line) ?: return null
        val uid = UID.find(line)?.groupValues?.get(1)?.toIntOrNull()
        return HomeStart(activity(match.groupValues[1], match.groupValues[2]), uid)
    }

    fun parseResume(line: String): Activity? {
        if (!line.contains("am_resume_activity")) return null
        val open = line.lastIndexOf('[')
        val close = line.lastIndexOf(']')
        if (open < 0 || close <= open) return null
        val component = line.substring(open + 1, close).split(',').last().trim()
        val slash = component.indexOf('/')
        if (slash <= 0 || slash == component.length - 1) return null
        return activity(component.substring(0, slash), component.substring(slash + 1))
    }

    private fun activity(pkg: String, cls: String) = Activity(pkg, if (cls.startsWith('.')) pkg + cls else cls)
}

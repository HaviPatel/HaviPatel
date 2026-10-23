package com.reatv.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.reatv.launcher.system.HomeRedirect
import com.reatv.launcher.ui.ReaRoot
import kotlinx.coroutines.flow.MutableSharedFlow

enum class HomeEvent { HomePressed, Resumed, Hidden }

class MainActivity : ComponentActivity() {
    internal val events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 8)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ReaRoot(activity = this, events = events) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        events.tryEmit(HomeEvent.HomePressed)
    }

    override fun onResume() {
        super.onResume()
        HomeRedirect.onLauncherVisible()
        events.tryEmit(HomeEvent.Resumed)
    }

    override fun onStop() {
        super.onStop()
        events.tryEmit(HomeEvent.Hidden)
    }
}

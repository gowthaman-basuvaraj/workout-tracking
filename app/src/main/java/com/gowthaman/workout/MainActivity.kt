package com.gowthaman.workout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.gowthaman.workout.data.Repo
import com.gowthaman.workout.ui.Root
import com.gowthaman.workout.ui.theme.UncomplicatedTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Hold the splash while the folder is being read, so the first thing you see is
        // your day rather than an empty one that fills in a beat later. Capped, because a
        // slow or unreachable folder must never leave the splash up indefinitely.
        val startedAt = System.currentTimeMillis()
        splash.setKeepOnScreenCondition {
            vm.status.value == Repo.Status.LOADING &&
                System.currentTimeMillis() - startedAt < MAX_SPLASH_HOLD_MS
        }

        setContent {
            UncomplicatedTheme {
                Root(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-read the folder so edits made on a laptop, or pulled in by a sync client while
        // the app sat in the background, are visible straight away.
        vm.refresh()
    }

    private companion object {
        const val MAX_SPLASH_HOLD_MS = 1500L
    }
}

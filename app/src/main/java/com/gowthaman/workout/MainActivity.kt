package com.gowthaman.workout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.gowthaman.workout.ui.Root
import com.gowthaman.workout.ui.theme.WorkoutTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            WorkoutTheme {
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
}

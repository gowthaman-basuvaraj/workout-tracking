package com.gowthaman.workout.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.gowthaman.workout.data.today

@Composable
fun Spike() {
    Text("today is ${today()}")
}

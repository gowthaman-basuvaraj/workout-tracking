package com.gowthaman.workout.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The question mark in a circle: the help affordance people already look for.
 *
 * Drawn in Kotlin rather than loaded as a resource. It used to be an Android vector
 * drawable, which does not exist on another platform, and the alternative -- bringing in
 * the Compose resources plugin and its generated accessors -- is a lot of machinery for
 * one path. Material's icon set has no outlined question mark in the core artifact
 * either, and an "i" for information is a different affordance.
 */
val HelpIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Help",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            // The dot.
            moveTo(11f, 18f); horizontalLineToRelative(2f); verticalLineToRelative(-2f)
            horizontalLineToRelative(-2f); close()
            // The ring.
            moveTo(12f, 2f)
            curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
            reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
            reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
            reflectiveCurveTo(17.52f, 2f, 12f, 2f)
            close()
            moveTo(12f, 20f)
            curveToRelative(-4.41f, 0f, -8f, -3.59f, -8f, -8f)
            reflectiveCurveToRelative(3.59f, -8f, 8f, -8f)
            reflectiveCurveToRelative(8f, 3.59f, 8f, 8f)
            reflectiveCurveToRelative(-3.59f, 8f, -8f, 8f)
            close()
            // The hook.
            moveTo(12f, 6f)
            curveToRelative(-2.21f, 0f, -4f, 1.79f, -4f, 4f)
            horizontalLineToRelative(2f)
            curveToRelative(0f, -1.1f, 0.9f, -2f, 2f, -2f)
            reflectiveCurveToRelative(2f, 0.9f, 2f, 2f)
            curveToRelative(0f, 2f, -3f, 1.75f, -3f, 5f)
            horizontalLineToRelative(2f)
            curveToRelative(0f, -2.25f, 3f, -2.5f, 3f, -5f)
            curveToRelative(0f, -2.21f, -1.79f, -4f, -4f, -4f)
            close()
        }
    }.build()
}

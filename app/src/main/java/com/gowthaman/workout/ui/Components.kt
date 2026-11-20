package com.gowthaman.workout.ui

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gowthaman.workout.data.FoodScale
import com.gowthaman.workout.data.SleepQuality
import com.gowthaman.workout.data.StressDirection
import com.gowthaman.workout.ui.theme.NumberStyle

/**
 * Colour for one point of the healthy-to-indulgent scale.
 *
 * Deliberately not red at the indulgent end: the app reports, the reader judges. Teal /
 * grey / blue stay distinguishable without the page telling anyone off.
 */
/**
 * Chips laid out across as many lines as they need.
 *
 * A horizontally scrolling row hides the current selection whenever it sits past the right
 * edge — which is exactly what happened with five meal purposes on a narrow phone.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChipFlow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp),
    content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit,
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** Centred variant, for the action row under an [EmptyState]. */
@Composable
fun CenteredChipFlow(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit,
) = ChipFlow(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    content = content,
)

@Composable
fun scaleAccent(scale: FoodScale): Color = when (scale) {
    FoodScale.HEALTHY -> MaterialTheme.colorScheme.tertiary
    FoodScale.EVERYDAY -> MaterialTheme.colorScheme.outline
    FoodScale.INDULGENT -> MaterialTheme.colorScheme.primary
}

/** Container and content colours for a scale pill. */
@Composable
fun scalePillColors(scale: FoodScale): Pair<Color, Color> = when (scale) {
    FoodScale.HEALTHY ->
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    FoodScale.EVERYDAY ->
        MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    FoodScale.INDULGENT ->
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
}

/** Same three-step treatment as the food scale: teal, grey, blue. Never red. */
@Composable
fun qualityAccent(quality: SleepQuality): Color = when (quality) {
    SleepQuality.GOOD -> MaterialTheme.colorScheme.tertiary
    SleepQuality.OK -> MaterialTheme.colorScheme.outline
    SleepQuality.POOR -> MaterialTheme.colorScheme.primary
}

@Composable
fun qualityPillColors(quality: SleepQuality): Pair<Color, Color> = when (quality) {
    SleepQuality.GOOD ->
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    SleepQuality.OK ->
        MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    SleepQuality.POOR ->
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
}

/** Busters read as the calmer colour; neither direction is "bad". */
@Composable
fun directionPillColors(direction: StressDirection): Pair<Color, Color> = when (direction) {
    StressDirection.BUSTER ->
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    StressDirection.ENHANCER ->
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
}

@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            if (title != null || action != null) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (title != null) {
                        Text(
                            title.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    action?.invoke()
                }
                Spacer(Modifier.height(10.dp))
            }
            content()
        }
    }
}

@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    accent: Color? = null,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = accent ?: MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (sub != null) {
                Text(
                    sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * @param small a quieter second rank, for things that qualify a pill rather than being
 *   one in their own right. Same shape, so a row of them still lines up with the row
 *   above it.
 */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    small: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .background(container)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            // Horizontal padding stays put so a small pill's text lines up with the text
            // in the pill above it, not just its edge. Only the height and the type come
            // down.
            .padding(horizontal = 11.dp, vertical = if (small) 3.dp else 6.dp)
    ) {
        Text(
            text,
            style = if (small) MaterialTheme.typography.labelSmall
            else MaterialTheme.typography.labelMedium,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A short confirmation.
 *
 * A toast rather than a snackbar because some of these fire as the screen is closing, and
 * a snackbar lives inside the screen that is going away -- which is precisely the moment
 * the message is worth reading.
 */
@Composable
fun rememberToaster(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { message: String -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }
}

@Composable
fun NumberText(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    Text(
        text,
        modifier = modifier,
        style = NumberStyle,
        color = color ?: MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

/**
 * A number field with -/+ buttons, because the point of this app is tapping a rep count
 * up by one without opening a keyboard.
 */
@Composable
fun StepperField(
    label: String,
    text: String,
    onTextChange: (String) -> Unit,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    enabled: Boolean = true,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        StepButton("−", enabled) { onStep(-1) }
        Spacer(Modifier.width(6.dp))
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            label = { Text(label) },
            suffix = suffix?.let { { Text(it) } },
            singleLine = true,
            enabled = enabled,
            textStyle = NumberStyle,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        StepButton("+", enabled) { onStep(1) }
    }
}

@Composable
private fun StepButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                symbol,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
fun QuickPickRow(
    options: List<String>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return
    LazyRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(options) { option ->
            Pill(text = option, onClick = { onPick(option) })
        }
    }
}

data class Bar(val label: String, val value: Float, val highlight: Boolean = false)

/** Bars drawn on a Canvas, with labels laid out below as real text so they stay legible. */
@Composable
fun BarChart(
    bars: List<Bar>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 96.dp,
) {
    if (bars.isEmpty()) return
    val max = bars.maxOf { it.value }.coerceAtLeast(1f)
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            val slot = size.width / bars.size
            val barWidth = (slot * 0.56f).coerceAtMost(34f * density)
            bars.forEachIndexed { i, bar ->
                val h = (bar.value / max) * size.height
                val left = i * slot + (slot - barWidth) / 2f
                // Zero-height bars would vanish; a hairline keeps the axis readable.
                val drawn = h.coerceAtLeast(2f)
                drawRoundRect(
                    color = if (bar.highlight) accent else muted,
                    topLeft = Offset(left, size.height - drawn),
                    size = Size(barWidth, drawn),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            bars.forEach { bar ->
                Text(
                    bar.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A single trend line — used for estimated-1RM history on the exercise screen. */
@Composable
fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 88.dp,
) {
    if (values.size < 2) return
    val min = values.min()
    val max = values.max()
    val span = (max - min).takeIf { it > 0.0001f } ?: 1f
    val line = MaterialTheme.colorScheme.primary
    val fill = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val stepX = if (values.size == 1) 0f else size.width / (values.size - 1)
        val pad = size.height * 0.1f
        fun y(v: Float) = size.height - pad - ((v - min) / span) * (size.height - pad * 2)

        val path = Path().apply {
            moveTo(0f, y(values.first()))
            values.forEachIndexed { i, v -> if (i > 0) lineTo(i * stepX, y(v)) }
        }
        val area = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(area, fill)
        drawPath(path, line, style = Stroke(width = 3f * density))
        values.forEachIndexed { i, v ->
            drawCircle(line, radius = 2.5f * density, center = Offset(i * stepX, y(v)))
        }
    }
}

@Composable
fun IconTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier) { Text(text) }
}

@Composable
fun SmallIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = modifier.size(36.dp)) {
        Icon(
            icon,
            contentDescription = description,
            modifier = Modifier.size(19.dp),
            tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

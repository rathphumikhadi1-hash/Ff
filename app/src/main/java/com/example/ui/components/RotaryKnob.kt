package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ConsoleBorder
import com.example.ui.theme.ConsoleSurface
import com.example.ui.theme.ConsoleSurfaceElevated
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RotaryKnob(
    label: String,
    valueText: String,
    fraction: Float, // 0f..1f
    defaultFraction: Float = 0f,
    accentColor: Color,
    enabled: Boolean = true,
    testTagName: String,
    onFractionChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember(fraction) { mutableFloatStateOf(fraction) }
    val currentOnFractionChange by rememberUpdatedState(onFractionChange)

    val effectiveColor = if (enabled) accentColor else TextMuted

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (isDragging) ConsoleSurfaceElevated else ConsoleSurface)
            .border(
                width = 1.dp,
                color = if (isDragging) effectiveColor.copy(alpha = 0.7f) else ConsoleBorder,
                shape = RoundedCornerShape(18.dp),
            )
            .padding(vertical = 14.dp, horizontal = 10.dp)
            .semantics { contentDescription = "$label $valueText" }
            .testTag(testTagName),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(92.dp)
                .pointerInput(defaultFraction) {
                    detectTapGestures(
                        onDoubleTap = {
                            dragFraction = defaultFraction
                            currentOnFractionChange(defaultFraction)
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            isDragging = true
                            dragFraction = fraction
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val next = (dragFraction - dragAmount / 240f).coerceIn(0f, 1f)
                            dragFraction = next
                            currentOnFractionChange(next)
                        }
                    )
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeW = 7.dp.toPx()
                val inset = strokeW + 6.dp.toPx()
                val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
                val topLeft = Offset(inset, inset)
                val startAngle = 135f
                val sweepTotal = 270f

                // Outer tick ring
                val center = Offset(size.width / 2f, size.height / 2f)
                val tickRadiusOuter = size.minDimension / 2f - 1.dp.toPx()
                val tickRadiusInner = tickRadiusOuter - 4.dp.toPx()
                for (t in 0..10) {
                    val deg = startAngle + (t / 10f) * sweepTotal
                    val rad = deg * (PI / 180.0)
                    val isLit = (t / 10f) <= fraction + 0.01f && fraction > 0.01f
                    drawLine(
                        color = if (isLit && enabled) effectiveColor else ConsoleBorder,
                        start = Offset(
                            center.x + (tickRadiusInner * cos(rad)).toFloat(),
                            center.y + (tickRadiusInner * sin(rad)).toFloat(),
                        ),
                        end = Offset(
                            center.x + (tickRadiusOuter * cos(rad)).toFloat(),
                            center.y + (tickRadiusOuter * sin(rad)).toFloat(),
                        ),
                        strokeWidth = 1.6.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }

                // Background arc
                drawArc(
                    color = ObsidianBg,
                    startAngle = startAngle,
                    sweepAngle = sweepTotal,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round),
                )

                // Active arc
                val activeSweep = (fraction.coerceIn(0f, 1f)) * sweepTotal
                if (activeSweep > 1f) {
                    drawArc(
                        color = effectiveColor,
                        startAngle = startAngle,
                        sweepAngle = activeSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round),
                    )
                }

                // Inner metallic knob dial
                val dialRadius = (arcSize.minDimension / 2f) - strokeW * 0.85f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF283244), Color(0xFF121722)),
                        center = Offset(center.x - dialRadius * 0.25f, center.y - dialRadius * 0.25f),
                        radius = dialRadius * 1.4f,
                    ),
                    radius = dialRadius,
                    center = center,
                )

                // Pointer notch
                val pointerAngleRad = (startAngle + activeSweep) * (PI / 180.0)
                val pointerStart = dialRadius * 0.42f
                val pointerEnd = dialRadius * 0.84f
                drawLine(
                    color = effectiveColor,
                    start = Offset(
                        center.x + (pointerStart * cos(pointerAngleRad)).toFloat(),
                        center.y + (pointerStart * sin(pointerAngleRad)).toFloat(),
                    ),
                    end = Offset(
                        center.x + (pointerEnd * cos(pointerAngleRad)).toFloat(),
                        center.y + (pointerEnd * sin(pointerAngleRad)).toFloat(),
                    ),
                    strokeWidth = 2.6.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }

        Text(
            text = valueText,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) effectiveColor else TextSecondary,
        )
    }
}
